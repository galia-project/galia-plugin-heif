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

import is.galia.codec.xmp.XMPUtils;
import is.galia.operation.Encode;
import is.galia.plugin.heif.test.TestUtils;
import is.galia.processor.Java2DUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HEIFEncoderTest {

    private static final boolean SAVE_IMAGES = true;

    private Arena arena = Arena.ofConfined();
    private final HEIFEncoder encoder = new HEIFEncoder();

    @BeforeAll
    public static void beforeClass() {
        try (HEIFEncoder encoder = new HEIFEncoder()) {
            encoder.onApplicationStart();
        }
    }

    @BeforeEach
    void setUp() {
        encoder.setArena(arena);
        encoder.initializePlugin();
    }

    @AfterEach
    void tearDown() {
        encoder.close();
        arena.close();
    }

    //region Plugin methods

    @Test
    void getPluginConfigKeys() {
        Set<String> keys = encoder.getPluginConfigKeys();
        assertFalse(keys.isEmpty());
    }

    @Test
    void getPluginName() {
        assertEquals(HEIFEncoder.class.getSimpleName(),
                encoder.getPluginName());
    }

    //endregion
    //region Encoder methods

    /* write() */

    @Test
    void encodeToAVIF() throws Exception {
        BufferedImage image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.AVIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "avif");
        }
    }

    @Test
    void encodeToHEIC() throws Exception {
        BufferedImage image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.HEIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "heic");
        }
    }

    @Test
    void encodeWithBufferedImageTypeByteGray() throws Exception {
        BufferedImage image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_BYTE_GRAY);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.AVIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "avif");
        }
    }

    @Test
    void encodeWithBufferedImageTypeIntRGB() throws Exception {
        BufferedImage image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.AVIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "avif");
        }
    }

    @Test
    void encodeWithBufferedImageTypeIntARGB() throws Exception {
        BufferedImage image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.AVIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "avif");
        }
    }

    @Test
    void encodeWithBufferedImageType3ByteBGR() throws Exception {
        BufferedImage image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_3BYTE_BGR);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.AVIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "avif");
        }
    }

    @Test
    void encodeWithBufferedImageType4ByteABGR() throws Exception {
        BufferedImage image = Java2DUtils.newTestPatternImage(
                300, 300, BufferedImage.TYPE_4BYTE_ABGR);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.AVIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (SAVE_IMAGES) TestUtils.save(bytes, "avif");
        }
    }

    @Test
    void encodeWithGray() throws Exception {
        BufferedImage image = ImageIO.read(TestUtils.getFixture("gray.jpg").toFile());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.AVIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();

            assertTrue(bytes.length > 1000);
            if (SAVE_IMAGES) TestUtils.save(bytes, "avif");
        }
    }

    @Test
    void encodeWithRGB() throws Exception {
        BufferedImage image = ImageIO.read(TestUtils.getFixture("rgb.png").toFile());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.AVIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();

            assertTrue(bytes.length > 1000);
            if (SAVE_IMAGES) TestUtils.save(bytes, "avif");
        }
    }

    @Test
    void encodeWithRGBA() throws Exception {
        BufferedImage image = ImageIO.read(TestUtils.getFixture("alpha.png").toFile());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.HEIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();

            assertTrue(bytes.length > 1000);
            if (SAVE_IMAGES) TestUtils.save(bytes, "heic");
        }
    }

    @Test
    void encodeWithXMP() throws Exception {
        BufferedImage image = ImageIO.read(TestUtils.getFixture("rgb.png").toFile());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(Formats.HEIF);
            String xmp = Files.readString(TestUtils.getFixture("xmp.xmp"));
            encode.setXMP(XMPUtils.trimXMP(xmp));
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();

            String str = new String(bytes, StandardCharsets.UTF_8);
            assertTrue(str.contains("<rdf:RDF"));
            if (SAVE_IMAGES) TestUtils.save(bytes, "heic");
        }
    }

    @Test
    void encodeWithOddDimensions() throws Exception {
        BufferedImage image = Java2DUtils.newTestPatternImage(
                301, 301, BufferedImage.TYPE_3BYTE_BGR);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.setEncode(new Encode(Formats.HEIF));
            encoder.encode(image, outputStream);
            byte[] bytes = outputStream.toByteArray();

            assertTrue(bytes.length > 500);
            if (SAVE_IMAGES) TestUtils.save(bytes, "heic");
        }
    }

    @Test
    void encodeWithLosslessOption() throws Exception {
        BufferedImage image = ImageIO.read(TestUtils.getFixture("rgb.png").toFile());
        byte[] image1, image2;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(Formats.HEIF);
            encode.setOption(ConfigurationKey.HEIFENCODER_LOSSLESS.key(), true);
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            image1 = outputStream.toByteArray();
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(Formats.HEIF);
            encode.setOption(ConfigurationKey.HEIFENCODER_LOSSLESS.key(), false);
            encode.setOption(ConfigurationKey.HEIFENCODER_QUALITY.key(), 5);
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            image2 = outputStream.toByteArray();
        }
        assertNotEquals(image1.length, image2.length);
    }

    @Test
    void encodeWithPresetOption() throws Exception {
        BufferedImage image = ImageIO.read(TestUtils.getFixture("rgb.png").toFile());
        byte[] image1, image2;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(Formats.HEIF);
            encode.setOption(ConfigurationKey.HEIFENCODER_PRESET.key(), "ultrafast");
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            image1 = outputStream.toByteArray();
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(Formats.HEIF);
            encode.setOption(ConfigurationKey.HEIFENCODER_PRESET.key(), "veryslow");
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            image2 = outputStream.toByteArray();
        }
        assertTrue(image1.length < image2.length);
    }

    @Test
    void encodeWithQualityOption() throws Exception {
        BufferedImage image = ImageIO.read(TestUtils.getFixture("rgb.png").toFile());
        byte[] image1, image2;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(Formats.HEIF);
            encode.setOption(ConfigurationKey.HEIFENCODER_QUALITY.key(), 90);
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            image1 = outputStream.toByteArray();
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(Formats.HEIF);
            encode.setOption(ConfigurationKey.HEIFENCODER_QUALITY.key(), 10);
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            image2 = outputStream.toByteArray();
        }
        assertNotEquals(image1.length, image2.length);
    }

    @Test
    void encodeWithSpeedOption() throws Exception {
        BufferedImage image = ImageIO.read(TestUtils.getFixture("rgb.png").toFile());
        byte[] image1, image2;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(Formats.AVIF);
            encode.setOption(ConfigurationKey.HEIFENCODER_SPEED.key(), 9);
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            image1 = outputStream.toByteArray();
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Encode encode = new Encode(Formats.AVIF);
            encode.setOption(ConfigurationKey.HEIFENCODER_SPEED.key(), 1);
            encoder.setEncode(encode);
            encoder.encode(image, outputStream);
            image2 = outputStream.toByteArray();
        }
        assertTrue(image1.length < image2.length);
    }

}
