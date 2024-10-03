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

import is.galia.codec.AbstractDecoder;
import is.galia.codec.Decoder;
import is.galia.codec.DecoderHint;
import is.galia.codec.SourceFormatException;
import is.galia.codec.tiff.Directory;
import is.galia.codec.tiff.DirectoryReader;
import is.galia.codec.tiff.EXIFBaselineTIFFTagSet;
import is.galia.codec.tiff.EXIFGPSTagSet;
import is.galia.codec.tiff.EXIFInteroperabilityTagSet;
import is.galia.codec.tiff.EXIFTagSet;
import is.galia.image.ComponentOrder;
import is.galia.image.Format;
import is.galia.image.Metadata;
import is.galia.image.MutableMetadata;
import is.galia.image.ReductionFactor;
import is.galia.image.Region;
import is.galia.image.Size;
import is.galia.plugin.Plugin;
import is.galia.plugin.heif.ffi.heif_error;
import is.galia.plugin.heif.ffi.heif_reader;
import is.galia.processor.Java2DUtils;
import is.galia.stream.ByteArrayImageInputStream;
import is.galia.stream.PathImageInputStream;
import is.galia.util.FileUtils;
import is.galia.util.IOUtils;
import is.galia.util.SoftwareVersion;
import is.galia.util.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.stream.ImageInputStream;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static is.galia.plugin.heif.ffi.heif_h.C_INT;
import static is.galia.plugin.heif.ffi.heif_h.C_POINTER;
import static is.galia.plugin.heif.ffi.heif_h.LIBHEIF_VERSION;
import static is.galia.plugin.heif.ffi.heif_h.heif_channel_interleaved;
import static is.galia.plugin.heif.ffi.heif_h.heif_chroma_interleaved_RGB;
import static is.galia.plugin.heif.ffi.heif_h.heif_chroma_interleaved_RGBA;
import static is.galia.plugin.heif.ffi.heif_h.heif_colorspace_RGB;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_alloc;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_free;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_get_image_handle;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_get_list_of_top_level_image_IDs;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_get_number_of_top_level_images;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_read_from_file;
import static is.galia.plugin.heif.ffi.heif_h.heif_context_read_from_reader;
import static is.galia.plugin.heif.ffi.heif_h.heif_decode_image;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_get_plane_readonly;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_get_height;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_get_list_of_metadata_block_IDs;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_get_list_of_thumbnail_IDs;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_get_metadata;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_get_metadata_size;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_get_number_of_thumbnails;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_get_raw_color_profile;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_get_raw_color_profile_size;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_get_thumbnail;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_get_width;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_has_alpha_channel;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_handle_release;
import static is.galia.plugin.heif.ffi.heif_h.heif_image_release;

/**
 * Implementation using the Java Foreign Function &amp; Memory API to call into
 * libheif.
 */
public final class HEIFDecoder extends AbstractDecoder
        implements Decoder, Plugin {

    /**
     * Container for various embedded image information.
     */
    private static abstract class AbstractHEIFImage implements AutoCloseable {
        final int id;
        /** A {@code heif_image_handle} */
        MemorySegment imageHandle;
        private Size size;

        AbstractHEIFImage(int id) {
            this.id = id;
        }

        @Override
        public void close() {
            if (imageHandle != null) {
                heif_image_handle_release(imageHandle);
                imageHandle = null;
            }
        }

        Size getSize() throws IOException {
            if (size == null) {
                getImageHandle();
                int width  = heif_image_handle_get_width(imageHandle);
                int height = heif_image_handle_get_height(imageHandle);
                size = new Size(width, height);
            }
            return size;
        }

        abstract MemorySegment getImageHandle() throws IOException;
        abstract boolean hasAlpha() throws IOException;
    }

    private class HEIFImage extends AbstractHEIFImage {

        private int hasAlpha = -1, numThumbnails = -1;
        private Directory exif;
        private byte[] xmp;
        /** IDs of all thumbnails of the image, in index order */
        private int[] thumbIDs;
        /** N.B.: keys are thumbnail IDs */
        private final Map<Integer,HEIFThumbnail> thumbnails = new HashMap<>();

        HEIFImage(int id) {
            super(id);
        }

        Directory getEXIF() throws IOException {
            if (exif == null) {
                getImageHandle();

                MemorySegment typeFilter = arena.allocateFrom("Exif");
                MemorySegment exifID     = arena.allocate(C_INT);
                int n = heif_image_handle_get_list_of_metadata_block_IDs(
                        imageHandle, typeFilter, exifID, 1);
                if (n == 1) {
                    int exifIDInt       = exifID.get(ValueLayout.JAVA_INT, 0);
                    long size           = heif_image_handle_get_metadata_size(
                            imageHandle, exifIDInt);
                    MemorySegment data  = arena.allocate(size);
                    MemorySegment error = heif_image_handle_get_metadata(
                            arena, imageHandle, exifIDInt, data);
                    handleError(error);

                    int offset = 10;
                    byte[] exifBytes = data.asSlice(offset, size - offset)
                            .toArray(ValueLayout.JAVA_BYTE);
                    DirectoryReader reader = new DirectoryReader();
                    try (ImageInputStream is = new ByteArrayImageInputStream(exifBytes)) {
                        reader.setSource(is);
                        reader.addTagSet(new EXIFBaselineTIFFTagSet());
                        reader.addTagSet(new EXIFTagSet());
                        reader.addTagSet(new EXIFGPSTagSet());
                        reader.addTagSet(new EXIFInteroperabilityTagSet());
                        exif = reader.readFirst();
                    }
                }
            }
            return exif;
        }

        @Override
        MemorySegment getImageHandle() throws IOException {
            if (imageHandle == null) {
                initContext();
                MemorySegment imageHandlePtr = arena.allocate(C_POINTER);
                MemorySegment error = heif_context_get_image_handle(
                        arena, heifCtx, id, imageHandlePtr);
                handleError(error);
                imageHandle = imageHandlePtr.get(C_POINTER, 0);
            }
            return imageHandle;
        }

        int getNumThumbnails() throws IOException {
            if (numThumbnails == -1) {
                getImageHandle();
                numThumbnails = heif_image_handle_get_number_of_thumbnails(imageHandle);
            }
            return numThumbnails;
        }

        HEIFThumbnail getThumbnail(int thumbIndex) throws IOException {
            validateThumbIndex(thumbIndex);
            if (thumbIDs == null) {
                MemorySegment idArray = arena.allocate(
                        C_POINTER.byteSize() * getNumThumbnails());
                int result = heif_image_handle_get_list_of_thumbnail_IDs(
                        imageHandle, idArray, getNumThumbnails()); // TODO: what is result?
                thumbIDs = idArray.toArray(ValueLayout.JAVA_INT);
                // The array may have more entries than there are thumbnails...
                for (int i = 0, count = getNumThumbnails(); i < count; i++) {
                    int thumbID = thumbIDs[i];
                    thumbnails.put(thumbID, new HEIFThumbnail(this, thumbID));
                }
            }
            int thumbID = thumbIDs[thumbIndex];
            return thumbnails.get(thumbID);
        }

        byte[] getXMP() throws IOException {
            if (xmp == null) {
                getImageHandle();
                MemorySegment typeFilter = arena.allocateFrom("mime");
                MemorySegment xmpID = arena.allocate(C_INT);
                int n = heif_image_handle_get_list_of_metadata_block_IDs(
                        imageHandle, typeFilter, xmpID, 1);
                if (n == 1) {
                    int xmpIDInt = xmpID.get(ValueLayout.JAVA_INT, 0);
                    long size = heif_image_handle_get_metadata_size(
                            imageHandle, xmpIDInt);
                    MemorySegment data = arena.allocate(size);
                    MemorySegment error = heif_image_handle_get_metadata(
                            arena, imageHandle, xmpIDInt, data);
                    handleError(error);

                    xmp = data.reinterpret(size)
                            .toArray(ValueLayout.JAVA_BYTE);
                }
            }
            return xmp;
        }

        @Override
        boolean hasAlpha() throws IOException {
            if (hasAlpha == -1) {
                getImageHandle();
                hasAlpha = heif_image_handle_has_alpha_channel(imageHandle);
            }
            return hasAlpha == 1;
        }

        private void validateThumbIndex(int index) throws IOException {
            if (index < 0 || index >= getNumThumbnails()) {
                throw new IndexOutOfBoundsException();
            }
        }

    }

    private class HEIFThumbnail extends AbstractHEIFImage {

        private final HEIFImage parent;

        HEIFThumbnail(HEIFImage parent, int id) {
            super(id);
            this.parent = parent;
        }

        @Override
        MemorySegment getImageHandle() throws IOException {
            if (imageHandle == null) {
                initContext();
                MemorySegment thumbHandle = arena.allocate(C_POINTER);
                MemorySegment error       = heif_image_handle_get_thumbnail(
                        arena, parent.getImageHandle(), id, thumbHandle);
                handleError(error);
                imageHandle = thumbHandle.get(C_POINTER, 0);
            }
            return imageHandle;
        }

        @Override
        boolean hasAlpha() {
            return false;
        }
    }

    private static final Logger LOGGER =
            LoggerFactory.getLogger(HEIFDecoder.class);

    private static final AtomicBoolean IS_CLASS_INITIALIZED =
            new AtomicBoolean();

    static final Map<Long, HEIFDecoder> LIVE_INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MemorySegment heifCtx;
    /** N.B.: keys are image IDs */
    private final Map<Integer,HEIFImage> images = new HashMap<>();
    private boolean ownsInputStream;
    private transient int numImages;
    /** IDs of all images in the file, in index order */
    private transient int[] imageIDs;
    private transient Format format;
    private transient MutableMetadata metadata;

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

    //endregion
    //region Plugin methods

    @Override
    public Set<String> getPluginConfigKeys() {
        return Set.of();
    }

    @Override
    public String getPluginName() {
        return HEIFDecoder.class.getSimpleName();
    }

    @Override
    public void onApplicationStart() {
        if (!IS_CLASS_INITIALIZED.getAndSet(true)) {
            System.loadLibrary("heif");
            checkLibraryVersion();
            HEIFReaderFunctions.initializeClass();
        }
    }

    @Override
    public void onApplicationStop() {
    }

    @Override
    public void initializePlugin() {
        long threadID = Thread.currentThread().threadId();
        LIVE_INSTANCES.put(threadID, this);
    }

    //endregion
    //region Decoder methods

    @Override
    public void close() {
        super.close();
        images.values().forEach(HEIFImage::close);
        images.clear();
        if (heifCtx != null) {
            heif_context_free(heifCtx);
            heifCtx = null;
        }
        if (ownsInputStream) IOUtils.closeQuietly(inputStream);
        LIVE_INSTANCES.remove(Thread.currentThread().threadId());
    }

    @Override
    public Format detectFormat() throws IOException {
        if (format == null) {
            try {
                initSource();
                final int magicLength = 12;
                if (inputStream.length() >= magicLength) {
                    byte[] magic = new byte[magicLength];
                    inputStream.seek(0);
                    inputStream.readFully(magic);
                    if (magic[4] == 'f' && magic[5] == 't' && magic[6] == 'y' && magic[7] == 'p') {
                        // See: https://github.com/strukturag/libheif/blob/0a627a33d69cb5e93860536e841d23c8100f8065/examples/heif_dec.cc#L393
                        if (magic[8] == 'a' && magic[9] == 'v' && magic[10] == 'i' && magic[11] == 'f') {
                            format = Formats.AVIF;
                        } else if ((magic[8] == 'h' && magic[9] == 'e' && magic[10] == 'i' && magic[11] == 'c') ||
                                (magic[8] == 'h' && magic[9] == 'e' && magic[10] == 'i' && magic[11] == 'x') ||
                                (magic[8] == 'm' && magic[9] == 'i' && magic[10] == 'f' && magic[11] == '1')) {
                            format = Formats.HEIF;
                        }
                    }
                }
            } catch (SourceFormatException ignore) {
            }
            if (format == null) {
                format = Format.UNKNOWN;
            }
        }
        inputStream.seek(0);
        return format;
    }

    @Override
    public int getNumImages() throws IOException {
        if (numImages == 0) {
            initContext();
        }
        return numImages;
    }

    @Override
    public int getNumResolutions() throws IOException {
        initSource();
        return 1;
    }

    @Override
    public int getNumThumbnails(int imageIndex) throws IOException {
        HEIFImage image = getImage(imageIndex);
        return image.getNumThumbnails();
    }

    /**
     * @return Full source image dimensions.
     */
    @Override
    public Size getSize(int imageIndex) throws IOException {
        HEIFImage image = getImage(imageIndex);
        return image.getSize();
    }

    @Override
    public Set<Format> getSupportedFormats() {
        return Formats.all();
    }

    @Override
    public Size getThumbnailSize(int imageIndex,
                                 int thumbnailIndex) throws IOException {
        HEIFImage image = getImage(imageIndex);
        return image.getThumbnail(thumbnailIndex).getSize();
    }

    @Override
    public Size getTileSize(int imageIndex) throws IOException {
        return getSize(imageIndex);
    }

    @Override
    public BufferedImage decode(int imageIndex,
                                Region orientedRegion,
                                double[] scales,
                                ReductionFactor reductionFactor,
                                double[] diffScales,
                                Set<DecoderHint> decoderHints) throws IOException {
        HEIFImage heifImage = getImage(imageIndex);

        decoderHints.add(DecoderHint.ALREADY_ORIENTED);
        decoderHints.add(DecoderHint.IGNORED_REGION);
        decoderHints.add(DecoderHint.IGNORED_SCALE);
        diffScales[0] = diffScales[1] = 1;

        return readImage(heifImage);
    }

    @Override
    public Metadata readMetadata(int imageIndex) throws IOException {
        validateImageIndex(imageIndex);
        if (metadata == null) {
            Stopwatch watch = new Stopwatch();
            HEIFImage image = getImage(imageIndex);
            metadata        = new MutableMetadata();
            metadata.setEXIF(image.getEXIF());
            metadata.setXMP(image.getXMP());
            LOGGER.trace("readMetadata(): completed in {}", watch);
        }
        return metadata;
    }

    @Override
    public BufferedImage readThumbnail(int imageIndex,
                                       int thumbIndex) throws IOException {
        HEIFThumbnail heifThumbnail = getImage(imageIndex)
                .getThumbnail(thumbIndex);
        return readImage(heifThumbnail);
    }

    //endregion
    //region Private methods

    ImageInputStream getInputStream() {
        return inputStream;
    }

    private void validateImageIndex(int index) throws IOException {
        if (index < 0 || index >= getNumImages()) {
            throw new IndexOutOfBoundsException();
        }
    }

    private void initSource() throws IOException {
        if (imageFile == null && inputStream == null) {
            throw new IOException("Source not set");
        } else if (inputStream != null) {
            return;
        }
        try {
            FileUtils.checkReadableFile(imageFile);
            inputStream = new PathImageInputStream(imageFile);
            ownsInputStream = true;
        } catch (FileNotFoundException e) {
            throw new NoSuchFileException(e.getMessage());
        }
        try {
            if (!getSupportedFormats().contains(detectFormat())) {
                throw new SourceFormatException();
            }
            inputStream.seek(0);
        } catch (EOFException e) {
            throw new SourceFormatException();
        }
    }

    private void initContext() throws IOException {
        if (heifCtx != null) {
            return;
        }
        initSource();
        heifCtx = heif_context_alloc();

        if (imageFile != null) {
            String absPath         = imageFile.toAbsolutePath().toString();
            MemorySegment filename = arena.allocateFrom(absPath);
            MemorySegment error    = heif_context_read_from_file(
                    arena, heifCtx, filename, MemorySegment.NULL);
            handleError(error);
        } else {
            MemorySegment fileSizeFunction = Linker.nativeLinker().upcallStub(
                    HEIFReaderFunctions.WAIT_FOR_FILE_SIZE_FUNCTION,
                    HEIFReaderFunctions.WAIT_FOR_FILE_SIZE_FUNCTION_DESCRIPTOR,
                    arena);
            MemorySegment positionFunction = Linker.nativeLinker().upcallStub(
                    HEIFReaderFunctions.GET_POSITION_FUNCTION,
                    HEIFReaderFunctions.GET_POSITION_FUNCTION_DESCRIPTOR,
                    arena);
            MemorySegment readFunction = Linker.nativeLinker().upcallStub(
                    HEIFReaderFunctions.READ_FUNCTION,
                    HEIFReaderFunctions.READ_FUNCTION_DESCRIPTOR,
                    arena);
            MemorySegment seekFunction = Linker.nativeLinker().upcallStub(
                    HEIFReaderFunctions.SEEK_FUNCTION,
                    HEIFReaderFunctions.SEEK_FUNCTION_DESCRIPTOR,
                    arena);
            StructLayout readerLayout = (StructLayout) heif_reader.layout();
            MemorySegment reader      = arena.allocate(readerLayout);
            heif_reader.get_position(reader, positionFunction);
            heif_reader.read(reader, readFunction);
            heif_reader.seek(reader, seekFunction);
            heif_reader.wait_for_file_size(reader, fileSizeFunction);

            long threadID          = Thread.currentThread().threadId();
            MemorySegment userData = arena.allocateFrom(ValueLayout.JAVA_LONG, threadID);
            MemorySegment error    = heif_context_read_from_reader(
                    arena, heifCtx, reader, userData, MemorySegment.NULL);
            handleError(error);
        }
        numImages = heif_context_get_number_of_top_level_images(heifCtx);

        MemorySegment idArray = arena.allocate(C_POINTER.byteSize() * numImages);
        int result            = heif_context_get_list_of_top_level_image_IDs(
                heifCtx, idArray, numImages); // TODO: what is result?
        imageIDs              = idArray.toArray(ValueLayout.JAVA_INT);
    }

    private HEIFImage getImage(int imageIndex) throws IOException {
        validateImageIndex(imageIndex);
        int imageID = imageIDs[imageIndex];
        return images.computeIfAbsent(imageID, HEIFImage::new);
    }

    private BufferedImage readImage(AbstractHEIFImage heifImage) throws IOException {
        final Stopwatch watch     = new Stopwatch();
        MemorySegment imageHandle = heifImage.getImageHandle();
        MemorySegment imagePtr    = arena.allocate(C_POINTER);
        int chroma                = heifImage.hasAlpha() ?
                heif_chroma_interleaved_RGBA() : heif_chroma_interleaved_RGB();
        MemorySegment error       = heif_decode_image(
                arena, imageHandle, imagePtr, heif_colorspace_RGB(), chroma,
                MemorySegment.NULL);
        handleError(error);

        MemorySegment image = imagePtr.get(C_POINTER, 0);
        try {
            Size size = heifImage.getSize();

            MemorySegment stride = arena.allocate(C_INT);
            MemorySegment plane = heif_image_get_plane_readonly(
                    image, heif_channel_interleaved(), stride);
            int strideInt = stride.get(ValueLayout.JAVA_INT, 0);
            plane = plane.reinterpret((long) strideInt * size.intHeight());

            byte[] data = plane.toArray(ValueLayout.JAVA_BYTE);
            BufferedImage bufferedImage = newBufferedImage(
                    heifImage, size.intWidth(), size.intHeight(), strideInt, data);
            bufferedImage = applyICCProfile(imageHandle, bufferedImage);
            LOGGER.trace("readImage(): completed in {}", watch);
            return bufferedImage;
        } finally {
            if (image != null) {
                heif_image_release(image);
            }
        }
    }

    private BufferedImage newBufferedImage(AbstractHEIFImage heifImage,
                                           int width,
                                           int height,
                                           int bytesPerLine,
                                           byte[] samples) throws IOException {
        final Stopwatch watch = new Stopwatch();
        final int numBands    = heifImage.hasAlpha() ? 4 : 3;
        final int padding     = bytesPerLine - (width * numBands);

        BufferedImage image;
        // If the row stride equals the image width, we can construct a custom
        // BufferedImage backed by the array of decoded bytes we already have
        // without any copying.
        if (padding == 0) {
            ComponentOrder order = heifImage.hasAlpha() ?
                    ComponentOrder.ARGB : ComponentOrder.RGB;
            image = Java2DUtils.newImage(width, height, samples, order);
        } else {
            int type = (numBands == 4) ?
                    BufferedImage.TYPE_4BYTE_ABGR : BufferedImage.TYPE_3BYTE_BGR;
            image = new BufferedImage(width, height, type);
            WritableRaster raster = image.getRaster();
            int i = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int b = 0; b < numBands; b++) {
                        raster.setSample(x, y, b, samples[i]);
                        i++;
                    }
                }
                i += padding;
            }
        }
        LOGGER.trace("newBufferedImage() completed in {}", watch);
        return image;
    }

    private BufferedImage applyICCProfile(MemorySegment imageHandle,
                                          BufferedImage inImage) throws IOException {
        BufferedImage outImage = inImage;
        long profileSize = heif_image_handle_get_raw_color_profile_size(imageHandle);
        if (profileSize > 0) {
            MemorySegment rawProfile = arena.allocate(profileSize);
            MemorySegment error      = heif_image_handle_get_raw_color_profile(
                    arena, imageHandle, rawProfile);
            handleError(error);

            byte[] iccBytes = rawProfile.asSlice(0, profileSize)
                    .toArray(ValueLayout.JAVA_BYTE);
            ICC_Profile iccProfile = ICC_Profile.getInstance(iccBytes);
            try {
                outImage = Java2DUtils.convertToSRGB(inImage, iccProfile);
            } catch (IllegalArgumentException e) {
                if (("Numbers of source Raster bands and source color space " +
                        "components do not match").equals(e.getMessage())) {
                    LOGGER.debug("Failed to apply ICC profile: {}", e.getMessage());
                } else {
                    throw e;
                }
            }
        }
        return outImage;
    }

    private static void handleError(MemorySegment heifError) throws IOException {
        int code = heif_error.code(heifError);
        if (code != 0) {
            String message = heif_error.message(heifError).getString(0);
            throw new IOException(message);
        }
    }

}