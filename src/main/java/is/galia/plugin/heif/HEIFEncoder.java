/*
 * Copyright © 2024 Baird Creek Software LLC
 *
 * Licensed under the PolyForm Noncommercial License, version 1.0.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     https://polyformproject.org/licenses/noncommercial/1.0.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package is.galia.plugin.heif;

import is.galia.codec.Encoder;
import is.galia.codec.VariantFormatException;
import is.galia.image.Format;
import is.galia.operation.Encode;
import is.galia.plugin.Plugin;
import is.galia.plugin.heif.ffi.heif_color_profile_nclx;
import is.galia.plugin.heif.ffi.heif_encoding_options;
import is.galia.plugin.heif.ffi.heif_error;
import is.galia.plugin.heif.ffi.heif_writer;
import is.galia.util.SoftwareVersion;
import is.galia.util.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static is.galia.plugin.heif.ffi.heif_h.C_INT;
import static is.galia.plugin.heif.ffi.heif_h.C_POINTER;
import static is.galia.plugin.heif.ffi.heif_h.LIBHEIF_VERSION;
import static is.galia.plugin.heif.ffi.heif_h.heif_channel_Y;
import static is.galia.plugin.heif.ffi.heif_h.heif_channel_interleaved;
import static is.galia.plugin.heif.ffi.heif_h.heif_chroma_interleaved_RGB;
import static is.galia.plugin.heif.ffi.heif_h.heif_chroma_interleaved_RGBA;
import static is.galia.plugin.heif.ffi.heif_h.heif_chroma_monochrome;
import static is.galia.plugin.heif.ffi.heif_h.heif_colorspace_RGB;
import static is.galia.plugin.heif.ffi.heif_h.heif_colorspace_monochrome;
import static is.galia.plugin.heif.ffi.heif_h.heif_compression_AV1;
import static is.galia.plugin.heif.ffi.heif_h.heif_compression_HEVC;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_add_XMP_metadata;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_alloc;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_encode_image;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_free;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_get_encoder_for_format;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_write;
import static is.galia.plugin.heif.ffi.heif_h.heif_encoder_get_name;
import static is.galia.plugin.heif.ffi.heif_h.heif_encoder_release;
import static is.galia.plugin.heif.ffi.heif_h.heif_encoder_set_lossless;
import static is.galia.plugin.heif.ffi.heif_h.heif_encoder_set_lossy_quality;
import static is.galia.plugin.heif.ffi.heif_h.heif_encoder_set_parameter_integer;
import static is.galia.plugin.heif.ffi.heif_h.heif_encoder_set_parameter_string;
import static is.galia.plugin.heif.ffi.heif_h.heif_encoding_options_alloc;
import static is.galia.plugin.heif.ffi.heif_h.heif_encoding_options_free;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_add_plane;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_create;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_get_nclx_color_profile;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_get_plane;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_release;
import static is.galia.plugin.heif.ffi.heif_h.heif_matrix_coefficients_RGB_GBR;
import static is.galia.plugin.heif.ffi.heif_h.heif_nclx_color_profile_alloc;
import static is.galia.plugin.heif.ffi.heif_h.heif_nclx_color_profile_free;

/**
 * <p>Implementation using the Java Foreign Function & Memory API to call into
 * libheif.</p>
 */
public class HEIFEncoder implements Encoder, Plugin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(HEIFEncoder.class);

    private static final AtomicBoolean IS_CLASS_INITIALIZED =
            new AtomicBoolean();

    static final Map<String,OutputStream> OUTPUT_STREAMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Arena arena;
    private MemorySegment heifCtx;
    private Encode encode;
    private transient String outputStreamUUID;

    private static void checkLibraryVersion() {
        MemorySegment sgmt = LIBHEIF_VERSION();
        String version = sgmt.getString(0);
        LOGGER.debug("Detected libheif version {}", version);

        SoftwareVersion swVersion = SoftwareVersion.parse(version);
        if (swVersion.major() != 1 || swVersion.minor() < 18) {
            LOGGER.error("Incompatible libheif version. The required version " +
                    "is >= 1.18, < 2.");
        }
    }

    //region Plugin methods

    @Override
    public Set<String> getPluginConfigKeys() {
        return Arrays.stream(ConfigurationKey.values())
                .map(ConfigurationKey::toString)
                .filter(k -> k.contains(HEIFEncoder.class.getSimpleName()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPluginName() {
        return HEIFEncoder.class.getSimpleName();
    }

    @Override
    public void onApplicationStart() {
        if (!IS_CLASS_INITIALIZED.getAndSet(true)) {
            System.loadLibrary("heif");
            checkLibraryVersion();
            HEIFWriterFunctions.initializeClass();
        }
    }

    @Override
    public void onApplicationStop() {
    }

    @Override
    public void initializePlugin() {
        outputStreamUUID = UUID.randomUUID().toString();
    }

    //endregion
    //region Encoder methods

    @Override
    public void close() {
        if (heifCtx != null) heif_context_free(heifCtx);
        OUTPUT_STREAMS.remove(outputStreamUUID);
    }

    @Override
    public Set<Format> getSupportedFormats() {
        return Formats.all();
    }

    @Override
    public void setArena(Arena arena) {
        this.arena = arena;
    }

    @Override
    public void setEncode(Encode encode) {
        this.encode = encode;
    }

    @Override
    public void encode(RenderedImage renderedImage,
                       OutputStream outputStream) throws IOException {
        final Stopwatch elapsedTimer = new Stopwatch();

        initContext();
        OUTPUT_STREAMS.put(outputStreamUUID, outputStream);

        // Obtain an encoder for the Encode's format.
        int compression;
        if (Formats.AVIF.key().equals(encode.getFormat().key())) {
            compression = heif_compression_AV1();
        } else if (Formats.HEIF.key().equals(encode.getFormat().key())) {
            compression = heif_compression_HEVC();
        } else {
            throw new VariantFormatException();
        }

        MemorySegment options = null, encoder = null, image = null;
        try {
            MemorySegment encoderPtr = arena.allocate(C_POINTER);
            MemorySegment error      = heif_context_get_encoder_for_format(
                    arena, heifCtx, compression, encoderPtr);
            handleError(error);
            encoder = encoderPtr.get(C_POINTER, 0);

            final int width        = renderedImage.getWidth();
            final int height       = renderedImage.getHeight();
            final int numBands     = renderedImage.getSampleModel().getNumBands();
            final boolean hasAlpha = renderedImage.getColorModel().hasAlpha();

            // Create a heif_image.
            final int colorspace   = (numBands == 1) ?
                    heif_colorspace_monochrome() : heif_colorspace_RGB();
            final int chroma       = (numBands == 1) ?
                    heif_chroma_monochrome() :
                    (hasAlpha ? heif_chroma_interleaved_RGBA() : heif_chroma_interleaved_RGB());
            final int channel      = (numBands == 1) ?
                    heif_channel_Y() : heif_channel_interleaved();
            MemorySegment imagePtr = arena.allocate(C_POINTER);
            error                  = heif_image_create(
                    arena, width, height, colorspace, chroma, imagePtr);
            handleError(error);
            image = imagePtr.get(C_POINTER, 0);

            { // Set encoder options.
                options = heif_encoding_options_alloc();
                heif_encoding_options.save_alpha_channel(
                        options, (byte) (renderedImage.getColorModel().hasAlpha() ? 1 : 0));

                if (isLossless()) {
                    error = heif_encoder_set_lossless(arena, encoder, 1);
                    handleError(error);

                    MemorySegment nclx = heif_nclx_color_profile_alloc();
                    if (colorspace == heif_colorspace_RGB()) {
                        heif_color_profile_nclx.matrix_coefficients(
                                options, heif_matrix_coefficients_RGB_GBR());
                        heif_color_profile_nclx.full_range_flag(nclx, (byte) 1);
                        // set chroma=444
                        MemorySegment key   = arena.allocateFrom("chroma");
                        MemorySegment value = arena.allocateFrom("444");
                        error = heif_encoder_set_parameter_string(
                                arena, encoder, key, value);
                        handleError(error);
                    } else {
                        MemorySegment inputNCLX = heif_nclx_color_profile_alloc();
                        error = heif_image_get_nclx_color_profile(arena, image, inputNCLX);
                        handleError(error);
                        heif_color_profile_nclx.matrix_coefficients(nclx,
                                heif_color_profile_nclx.matrix_coefficients(inputNCLX));
                        heif_color_profile_nclx.transfer_characteristics(nclx,
                                heif_color_profile_nclx.transfer_characteristics(inputNCLX));
                        heif_color_profile_nclx.color_primaries(nclx,
                                heif_color_profile_nclx.color_primaries(inputNCLX));
                        heif_color_profile_nclx.full_range_flag(nclx,
                                heif_color_profile_nclx.full_range_flag(inputNCLX));
                        heif_nclx_color_profile_free(inputNCLX);
                    }
                    heif_encoding_options.output_nclx_profile(options, nclx);
                    //heif_encoding_options.image_orientation();
                } else {
                    error = heif_encoder_set_lossless(arena, encoder, 0);
                    handleError(error);
                    int quality = getQuality();
                    error = heif_encoder_set_lossy_quality(arena, encoder, quality);
                    handleError(error);
                    if (quality > 90) {
                        MemorySegment key   = arena.allocateFrom("chroma");
                        MemorySegment value = arena.allocateFrom("444");
                        error = heif_encoder_set_parameter_string(
                                arena, encoder, key, value);
                        handleError(error);
                    }
                }

                MemorySegment encoderName = heif_encoder_get_name(encoder);
                if (encoderName.getString(0).contains("x265")) {
                    // preset
                    MemorySegment key   = arena.allocateFrom("preset");
                    MemorySegment value = arena.allocateFrom(getPreset());
                    error = heif_encoder_set_parameter_string(
                            arena, encoder, key, value);
                    handleError(error);
                } else {
                    // speed
                    MemorySegment key = arena.allocateFrom("speed");
                    error = heif_encoder_set_parameter_integer(
                            arena, encoder, key, getSpeed());
                    handleError(error);
                }
            }

            // Add a plane to the heif_image.
            error = heif_image_add_plane(arena, image, channel, width, height, 8);
            handleError(error);

            // Copy the RenderedImage's Raster into native memory and fill up the
            // heif_image's plane with it.
            MemorySegment stride       = arena.allocate(C_INT); // a.k.a. bytes per line
            MemorySegment nativeBuffer = heif_image_get_plane(image, channel, stride);
            int strideInt = stride.get(ValueLayout.JAVA_INT, 0);
            nativeBuffer = nativeBuffer.reinterpret((long) strideInt * height);
            copyImageIntoNativeMemory(renderedImage, nativeBuffer, strideInt);

            MemorySegment imageHandlePtr = arena.allocate(C_POINTER);
            error = heif_context_encode_image(arena, heifCtx, image, encoder,
                    options, imageHandlePtr);
            handleError(error);

            // Add XMP, if present.
            if (encode.getXMP().isPresent()) {
                MemorySegment imageHandle = imageHandlePtr.get(C_POINTER, 0);
                MemorySegment xmpData = arena.allocateFrom(encode.getXMP().get());
                error = heif_context_add_XMP_metadata(
                        arena, heifCtx, imageHandle, xmpData, (int) xmpData.byteSize());
                handleError(error);
            }

            // Write the encoded image to the OutputStream using our custom write
            // function.
            MemorySegment writeFunction = Linker.nativeLinker().upcallStub(
                    HEIFWriterFunctions.WRITE_FUNCTION,
                    HEIFWriterFunctions.WRITE_FUNCTION_DESCRIPTOR,
                    arena);
            StructLayout writerStruct = (StructLayout) heif_writer.layout();
            MemorySegment writer = arena.allocate(writerStruct);
            heif_writer.writer_api_version(writer, 1);
            heif_writer.write(writer, writeFunction);

            MemorySegment userData = arena.allocateFrom(outputStreamUUID);
            error = heif_context_write(arena, heifCtx, writer, userData);
            handleError(error);
        } finally {
            if (options != null) heif_encoding_options_free(options);
            if (encoder != null) heif_encoder_release(encoder);
            if (image != null) heif_image_release(image);
        }
        LOGGER.trace("write(): total time: {}", elapsedTimer);
    }

    //endregion
    //region Private methods

    private void initContext() {
        if (heifCtx != null) {
            return;
        }
        heifCtx = heif_context_alloc();
    }

    private void copyImageIntoNativeMemory(RenderedImage image,
                                           MemorySegment nativeSamples,
                                           int bytesPerLine) {
        final Stopwatch watch       = new Stopwatch();
        final int width             = image.getWidth();
        final int height            = image.getHeight();
        final int numBands          = image.getSampleModel().getNumBands();
        final int padding           = bytesPerLine - (width * numBands);
        final Raster raster         = image.getData();
        final DataBuffer heapBuffer = raster.getDataBuffer();

        if (heapBuffer instanceof DataBufferByte && numBands == 1 && padding == 0) {
            // Unfortunately we can't use this technique for color images
            // because standard byte-type BufferedImages are in BGR order
            // whereas libheif wants RGB.
            nativeSamples.asByteBuffer().put(((DataBufferByte) heapBuffer).getData());
        } else {
            for (int i = 0, y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int b = 0; b < numBands; b++) {
                        byte sample = (byte) raster.getSample(x, y, b);
                        nativeSamples.set(ValueLayout.JAVA_BYTE, i++, sample);
                    }
                }
                i += padding;
            }
        }
        LOGGER.trace("Copied image into native memory in {}", watch);
    }

    private boolean isLossless() {
        return encode.getOptions().getBoolean(
                ConfigurationKey.HEIFENCODER_LOSSLESS.key(), false);
    }

    private String getPreset() {
        return encode.getOptions().getString(
                ConfigurationKey.HEIFENCODER_PRESET.key(), "fast");
    }

    private int getQuality() {
        return encode.getOptions().getInt(
                ConfigurationKey.HEIFENCODER_QUALITY.key(), 60);
    }

    private int getSpeed() {
        return encode.getOptions().getInt(
                ConfigurationKey.HEIFENCODER_SPEED.key(), 7);
    }

    private static void handleError(MemorySegment heifError) throws IOException {
        int code = heif_error.code(heifError);
        if (code != 0) {
            String message = heif_error.message(heifError).getString(0);
            throw new IOException(message);
        }
    }

}
