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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static is.galia.plugin.heif.ffi.heif_h.C_INT;
import static is.galia.plugin.heif.ffi.heif_h.C_LONG;
import static is.galia.plugin.heif.ffi.heif_h.C_POINTER;
import static is.galia.plugin.heif.ffi.heif_h.heif_reader_grow_status_size_beyond_eof;
import static is.galia.plugin.heif.ffi.heif_h.heif_reader_grow_status_size_reached;

/**
 * Contains callback functions that adapt an {@link ImageInputStream} for use
 * by {@code heif_reader}.
 */
final class HEIFReaderFunctions {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(HEIFReaderFunctions.class);

    static MethodHandle GET_POSITION_FUNCTION, READ_FUNCTION, SEEK_FUNCTION,
            WAIT_FOR_FILE_SIZE_FUNCTION;

    static final FunctionDescriptor GET_POSITION_FUNCTION_DESCRIPTOR =
            FunctionDescriptor.of(C_LONG, C_POINTER);
    static final FunctionDescriptor READ_FUNCTION_DESCRIPTOR =
            FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_POINTER);
    static final FunctionDescriptor SEEK_FUNCTION_DESCRIPTOR =
            FunctionDescriptor.of(C_INT, C_LONG, C_POINTER);
    static final FunctionDescriptor WAIT_FOR_FILE_SIZE_FUNCTION_DESCRIPTOR =
            FunctionDescriptor.of(C_INT, C_LONG, C_POINTER);

    static void initializeClass() {
        try {
            GET_POSITION_FUNCTION = MethodHandles.lookup().findStatic(
                    HEIFReaderFunctions.class, "getPosition",
                    GET_POSITION_FUNCTION_DESCRIPTOR.toMethodType());
            READ_FUNCTION = MethodHandles.lookup().findStatic(
                    HEIFReaderFunctions.class, "read",
                    READ_FUNCTION_DESCRIPTOR.toMethodType());
            SEEK_FUNCTION = MethodHandles.lookup().findStatic(
                    HEIFReaderFunctions.class, "seek",
                    SEEK_FUNCTION_DESCRIPTOR.toMethodType());
            WAIT_FOR_FILE_SIZE_FUNCTION = MethodHandles.lookup().findStatic(
                    HEIFReaderFunctions.class, "waitForFileSize",
                    WAIT_FOR_FILE_SIZE_FUNCTION_DESCRIPTOR.toMethodType());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            LOGGER.error(e.getMessage());
        }
    }

    static long getPosition(MemorySegment userData) {
        ImageInputStream is = fetchInputStream(userData);
        try {
            return is.getStreamPosition();
        } catch (IOException e) {
            LOGGER.error("getPosition(): {}", e.getMessage());
            return -1;
        }
    }

    static int read(MemorySegment buffer, long size, MemorySegment userData) {
        ImageInputStream is = fetchInputStream(userData);
        try {
            size = Math.min(size, is.length() - is.getStreamPosition());
            if (size < 1) {
                return -1;
            }
            byte[] bytes = new byte[(int) size];
            int read = is.read(bytes, 0, (int) size);
            if (read > 0) {
                buffer.reinterpret(size).asByteBuffer().put(bytes);
            }
            return 0;
        } catch (IOException e) {
            LOGGER.error("read(): {}", e.getMessage());
            return -1;
        }
    }

    static int seek(long pos, MemorySegment userData) {
        ImageInputStream inputStream = fetchInputStream(userData);
        try {
            final long length = inputStream.length();
            if (length < 0) {
                throw new IOException("Seeking requires an " +
                        ImageInputStream.class.getSimpleName() +
                        " implementation whose length() method returns a " +
                        "positive value.");
            } else if (pos < 0 || pos > length) {
                return -1;
            }
            inputStream.seek(pos);
            return 0;
        } catch (IOException e) {
            LOGGER.error("seek(): {}", e.getMessage());
            return -1;
        }
    }

    /**
     * <p>When calling this function, libheif wants to make sure that it can
     * read the file up to 'target_size'. This is useful when the file is
     * currently downloaded and may grow with time. You may, for example,
     * extract the image sizes even before the actual compressed image data has
     * been completely downloaded.</p>
     *
     * <p>Even if your input files will not grow, you will have to implement at
     * least detection whether the target_size is above the (fixed) file
     * length (in this case, return 'size_beyond_eof').</p>
     */
    static int waitForFileSize(long targetSize, MemorySegment userData) {
        ImageInputStream inputStream = fetchInputStream(userData);
        try {
            if (targetSize > inputStream.length()) {
                return heif_reader_grow_status_size_beyond_eof();
            }
            return heif_reader_grow_status_size_reached();
        } catch (IOException e) {
            LOGGER.error("waitForFileSize(): {}", e.getMessage());
            return -1;
        }
    }

    private static ImageInputStream fetchInputStream(MemorySegment userData) {
        final long threadID    = userData.get(ValueLayout.JAVA_LONG, 0);
        HEIFDecoder decoder = HEIFDecoder.LIVE_INSTANCES.get(threadID);
        return decoder.getInputStream();
    }

    private HEIFReaderFunctions() {}

}
