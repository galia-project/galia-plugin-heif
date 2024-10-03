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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static is.galia.plugin.heif.ffi.heif_h.C_INT;
import static is.galia.plugin.heif.ffi.heif_h.C_LONG;
import static is.galia.plugin.heif.ffi.heif_h.C_POINTER;

/**
 * Contains callback functions for {@code heif_writer}.
 */
final class HEIFWriterFunctions {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(HEIFWriterFunctions.class);

    static FunctionDescriptor WRITE_FUNCTION_DESCRIPTOR = FunctionDescriptor.of(
            C_INT, C_POINTER, C_POINTER, C_LONG, C_POINTER);
    static MethodHandle WRITE_FUNCTION;

    static void initializeClass() {
        try {
            WRITE_FUNCTION = MethodHandles.lookup().findStatic(
                    HEIFWriterFunctions.class, "write",
                    WRITE_FUNCTION_DESCRIPTOR.toMethodType());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            LOGGER.error(e.getMessage());
        }
    }

    static int write(MemorySegment heifCtx,
                     MemorySegment data,
                     long length,
                     MemorySegment userData) {
        OutputStream os = fetchOutputStream(userData);
        byte[] bytes = data.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
        try {
            os.write(bytes);
            return 0;
        } catch (IOException e) {
            LOGGER.error("write(): {}", e.getMessage());
            return -1;
        }
    }

    private static OutputStream fetchOutputStream(MemorySegment userData) {
        final String outputStreamUUID = userData.getString(0);
        return HEIFEncoder.OUTPUT_STREAMS.get(outputStreamUUID);
    }

    private HEIFWriterFunctions() {}

}
