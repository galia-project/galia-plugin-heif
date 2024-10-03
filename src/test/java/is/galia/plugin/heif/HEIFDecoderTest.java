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

import is.galia.codec.DecoderHint;
import is.galia.codec.SourceFormatException;
import is.galia.codec.tiff.Directory;
import is.galia.image.Region;
import is.galia.image.Size;
import is.galia.image.Format;
import is.galia.image.MediaType;
import is.galia.image.Metadata;
import is.galia.image.ReductionFactor;
import is.galia.plugin.heif.test.TestUtils;
import is.galia.stream.PathImageInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HEIFDecoderTest {

    private static final double DELTA         = 0.0000001;
    private static final Path DEFAULT_FIXTURE =
            TestUtils.getFixture("rgb-8bit.heic");
    private static final boolean SAVE_IMAGES  = true;

    private final Arena arena = Arena.ofConfined();
    private HEIFDecoder instance;

    @BeforeAll
    public static void beforeClass() {
        try (HEIFDecoder decoder = new HEIFDecoder()) {
            decoder.onApplicationStart();
        }
    }

    @BeforeEach
    public void setUp() {
        instance = new HEIFDecoder();
        instance.setArena(arena);
        instance.initializePlugin();
        instance.setSource(DEFAULT_FIXTURE);
    }

    @AfterEach
    public void tearDown() {
        instance.close();
        arena.close();
    }

    //region Plugin methods

    @Test
    void getPluginConfigKeys() {
        Set<String> keys = instance.getPluginConfigKeys();
        assertEquals(0, keys.size());
    }

    @Test
    void getPluginName() {
        assertEquals(HEIFDecoder.class.getSimpleName(),
                instance.getPluginName());
    }

    //endregion
    //region Decoder methods

    /* detectFormat() */

    @Test
    void detectFormatWithEmptyBytes() throws Exception {
        instance.setSource(TestUtils.getFixture("empty"));
        assertEquals(Format.UNKNOWN, instance.detectFormat());
    }

    @Test
    void detectFormatWithUnsupportedBytes() throws Exception {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertEquals(Format.UNKNOWN, instance.detectFormat());
    }

    @Test
    void detectFormatWithAVIFBrand() throws Exception {
        instance.setSource(TestUtils.getFixture("rgb-8bit.avif"));
        assertEquals(Formats.AVIF, instance.detectFormat());
    }

    @Test
    void detectFormatWithHEICBrand() throws Exception {
        assertEquals(Formats.HEIF, instance.detectFormat());
    }

    @Test
    void detectFormatWithHEIXBrand() throws Exception {
        instance.setSource(TestUtils.getFixture("Canon EOS R10.HIF"));
        assertEquals(Formats.HEIF, instance.detectFormat());
    }

    /* getNumImages() */

    @Test
    void getNumImagesWithSingleImageFile() throws Exception {
        assertEquals(1, instance.getNumImages());
    }

    @Test
    void getNumImagesWithMultiImageFile() throws Exception {
        instance.setSource(TestUtils.getFixture("multipage.heic"));
        assertEquals(9, instance.getNumImages());
    }

    /* getNumResolutions() */

    @Test
    void getNumResolutionsWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.getNumResolutions());
    }

    @Test
    void getNumResolutionsWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.getNumResolutions());
    }

    @Test
    void getNumResolutionsWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class,
                () -> instance.getNumResolutions());
    }

    @Test
    void getNumResolutions() throws Exception {
        assertEquals(1, instance.getNumResolutions());
    }

    /* getNumThumbnails() */

    @Test
    void getNumThumbnailsWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.getNumThumbnails(0));
    }

    @Test
    void getNumThumbnailsWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.getNumThumbnails(0));
    }

    @Test
    void getNumThumbnailsWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class,
                () -> instance.getNumThumbnails(0));
    }

    @Test
    void getNumThumbnailsWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getNumThumbnails(1));
    }

    @Test
    void getNumThumbnails() throws Exception {
        instance.setSource(TestUtils.getFixture("iPhone 12.HEIC"));
        assertEquals(1, instance.getNumThumbnails(0));
    }

    /* getSize() */

    @Test
    void getSizeWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () -> instance.getSize(0));
    }

    @Test
    void getSizeWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.getSize(0));
    }

    @Test
    void getSizeWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class, () -> instance.getSize(0));
    }

    @Test
    void getSizeWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getSize(1));
    }

    @Test
    void getSize() throws Exception {
        Size size = instance.getSize(0);
        assertEquals(64, size.intWidth());
        assertEquals(56, size.intHeight());
    }

    /* getSupportedFormats() */

    @Test
    void getSupportedFormats() {
        Set<Format> formats = instance.getSupportedFormats();
        assertEquals(2, formats.size());
        // AVIF
        Format format = formats.stream()
                .filter(f -> f.key().equals("avif")).findAny().orElseThrow();
        assertEquals("avif", format.key());
        assertEquals("AVIF", format.name());
        assertEquals(List.of(new MediaType("image", "avif")),
                format.mediaTypes());
        assertEquals(List.of("avif"),
                format.extensions());
        assertTrue(format.isRaster());
        assertFalse(format.isVideo());
        assertTrue(format.supportsTransparency());

        // HEIF
        format = formats.stream()
                .filter(f -> f.key().equals("heif")).findAny().orElseThrow();
        assertEquals("heif", format.key());
        assertEquals("HEIF", format.name());
        assertEquals(List.of(new MediaType("image", "heif"),
                new MediaType("image", "heif-sequence"),
                new MediaType("image", "heic"),
                new MediaType("image", "heic-sequence"),
                new MediaType("image", "avif")),
                format.mediaTypes());
        assertEquals(List.of("heif", "heifs", "heic", "heics", "avci", "avcs", "hif"),
                format.extensions());
        assertTrue(format.isRaster());
        assertFalse(format.isVideo());
        assertTrue(format.supportsTransparency());
    }

    /* getThumbnailSize() */

    @Test
    void getThumbnailSizeWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.getThumbnailSize(0, 0));
    }

    @Test
    void getThumbnailSizeWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.getThumbnailSize(0, 0));
    }

    @Test
    void getThumbnailSizeWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class,
                () -> instance.getThumbnailSize(0, 0));
    }

    @Test
    void getThumbnailSizeWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getThumbnailSize(1, 0));
    }

    @Test
    void getThumbnailSizeWithIllegalThumbnailIndex() {
        instance.setSource(TestUtils.getFixture("iPhone 12.HEIC"));
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getThumbnailSize(0, 1));
    }

    @Test
    void getThumbnailSize() throws Exception {
        instance.setSource(TestUtils.getFixture("iPhone 12.HEIC"));
        Size thumbSize = instance.getThumbnailSize(0, 0);
        assertEquals(320, thumbSize.intWidth());
        assertEquals(240, thumbSize.intHeight());
    }

    /* getTileSize() */

    @Test
    void getTileSizeWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.getTileSize(0));
    }

    @Test
    void getTileSizeWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.getTileSize(0));
    }

    @Test
    void getTileSizeWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class,
                () -> instance.getTileSize(0));
    }

    @Test
    void getTileSizeWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.getTileSize(1));
    }

    @Test
    void getTileSize() throws Exception {
        Size tileSize = instance.getTileSize(0);
        assertEquals(64, tileSize.intWidth());
        assertEquals(56, tileSize.intHeight());
    }

    /* read(int) */

    @Test
    void decode1WithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () -> instance.decode(0));
    }

    @Test
    void decode1WithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.decode(0));
    }

    @Test
    void decode1WithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class, () -> instance.decode(0));
    }

    @Test
    void decode1WithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.decode(1));
    }

    @Test
    void decode1FromFile() throws Exception {
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1FromStream() throws Exception {
        try (ImageInputStream is = new PathImageInputStream(DEFAULT_FIXTURE)) {
            instance.setSource(is);
            BufferedImage image = instance.decode(0);
            assertEquals(64, image.getWidth());
            assertEquals(56, image.getHeight());
            if (SAVE_IMAGES) TestUtils.save(image);
        }
    }

    @Test
    void decode1WithICCProfileAVIF() throws Exception {
        instance.setSource(TestUtils.getFixture("colorspin.avif"));
        BufferedImage image = instance.decode(0);
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithICCProfileHEIC() throws Exception {
        instance.setSource(TestUtils.getFixture("colorspin.heic"));
        BufferedImage image = instance.decode(0);
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    //region Brand tests

    @Test
    void decode1WithAVIFBrand() throws Exception {
        instance.setSource(TestUtils.getFixture("rgb-8bit.avif"));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithHEICBrand() throws Exception {
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithHEIXBrand() throws Exception {
        instance.setSource(TestUtils.getFixture("Canon EOS R10.HIF"));
        BufferedImage image = instance.decode(0);
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    //endregion

    @Test
    void decode1WithMultiPageImage() throws Exception {
        instance.setSource(TestUtils.getFixture("multipage.heic"));
        BufferedImage image = instance.decode(4);
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithGray8BitAVIF() throws Exception {
        instance.setSource(TestUtils.getFixture("gray-8bit.avif"));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithGray8BitHEIC() throws Exception {
        instance.setSource(TestUtils.getFixture("gray-8bit.heic"));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGB8BitAVIF() throws Exception {
        instance.setSource(TestUtils.getFixture("rgb-8bit.avif"));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGB8BitHEIC() throws Exception {
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGB10BitHEIC() throws Exception {
        instance.setSource(TestUtils.getFixture("Canon EOS R10.HIF"));
        BufferedImage image = instance.decode(0);
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGB12BitAVIF() throws Exception {
        instance.setSource(TestUtils.getFixture("rgb-12bit.avif"));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGB12BitHEIC() throws Exception {
        instance.setSource(TestUtils.getFixture("rgb-12bit.heic"));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGBA8BitAVIF() throws Exception {
        instance.setSource(TestUtils.getFixture("rgba-8bit.avif"));
        BufferedImage image = instance.decode(0);
        assertEquals(400, image.getWidth());
        assertEquals(300, image.getHeight());
        assertEquals(4, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGBA8BitHEIC() throws Exception {
        instance.setSource(TestUtils.getFixture("rgba-8bit.heic"));
        BufferedImage image = instance.decode(0);
        assertEquals(400, image.getWidth());
        assertEquals(300, image.getHeight());
        assertEquals(4, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGBA12BitAVIF() throws Exception {
        instance.setSource(TestUtils.getFixture("rgba-12bit.avif"));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(4, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    @Test
    void decode1WithRGBA12BitHEIC() throws Exception {
        instance.setSource(TestUtils.getFixture("rgba-12bit.heic"));
        BufferedImage image = instance.decode(0);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(4, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    /* read(int, ...) */

    @Test
    void decode2WithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class, () ->
                instance.decode(0,
                        new Region(0, 0, 9999, 9999),
                        new double[] { 1, 1 },
                        new ReductionFactor(),
                        null,
                        EnumSet.noneOf(DecoderHint.class)));
    }

    @Test
    void decode2WithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.decode(0,
                        new Region(0, 0, 9999, 9999),
                        new double[] { 1, 1 },
                        new ReductionFactor(),
                        null,
                        EnumSet.noneOf(DecoderHint.class)));
    }

    @Test
    void decode2WithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class, () ->
                instance.decode(0,
                        new Region(0, 0, 9999, 9999),
                        new double[] { 1, 1 },
                        new ReductionFactor(),
                        null,
                        EnumSet.noneOf(DecoderHint.class)));
    }

    @Test
    void decode2WithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.decode(1,
                        new Region(0, 0, 9999, 9999),
                        new double[] { 1, 1 },
                        new ReductionFactor(),
                        null,
                        EnumSet.noneOf(DecoderHint.class)));
    }

    @Test
    void decode2WithRegionAndScale() throws Exception {
        final Region roi                      = new Region(85, 105, 30, 50);
        final double[] scales                 = { 0.45, 0.45 };
        final ReductionFactor reductionFactor = new ReductionFactor();
        final double[] diffScales             = new double[2];
        final Set<DecoderHint> hints          = EnumSet.noneOf(DecoderHint.class);

        BufferedImage image = instance.decode(0, roi, scales,
                reductionFactor, diffScales, hints);
        assertEquals(64, image.getWidth());
        assertEquals(56, image.getHeight());
        assertEquals(0, reductionFactor.factor);
        assertEquals(1, diffScales[0], DELTA);
        assertEquals(1, diffScales[1], DELTA);
        assertTrue(hints.contains(DecoderHint.ALREADY_ORIENTED));
        assertTrue(hints.contains(DecoderHint.IGNORED_REGION));
        assertTrue(hints.contains(DecoderHint.IGNORED_SCALE));
        if (SAVE_IMAGES) TestUtils.save(image);
    }

    /* readMetadata() */

    @Test
    void decodeMetadataWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.readMetadata(0));
    }

    @Test
    void decodeMetadataWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.readMetadata(0));
    }

    @Test
    void decodeMetadataWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class,
                () -> instance.readMetadata(0));
    }

    @Test
    void decodeMetadataWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.readMetadata(9999));
    }

    @Test
    void decodeMetadataWithEXIF() throws Exception {
        instance.setSource(TestUtils.getFixture("iPhone 12.HEIC"));
        Metadata metadata = instance.readMetadata(0);
        if (metadata.getEXIF().isPresent()) {
            Directory dir = metadata.getEXIF().get();
            assertEquals(10, dir.size());
        } else {
            fail("No EXIF metadata");
        }
    }

    @Test
    void decodeMetadataWithXMP() throws Exception {
        instance.setSource(TestUtils.getFixture("Canon EOS R10.HIF"));
        Metadata metadata = instance.readMetadata(0);
        if (metadata.getXMP().isPresent()) {
            String xmp = metadata.getXMP().get();
            assertTrue(xmp.startsWith("<rdf:RDF"));
            assertTrue(xmp.endsWith("</rdf:RDF>"));
        } else {
            fail("No XMP metadata");
        }
    }

    /* readSequence() */

    @Test
    void decodeSequence() {
        assertThrows(UnsupportedOperationException.class,
                () -> instance.decodeSequence());
    }

    /* readThumbnail() */

    @Test
    void decodeThumbnailWithNonexistentImage() {
        instance.setSource(TestUtils.getFixture("bogus"));
        assertThrows(NoSuchFileException.class,
                () -> instance.readThumbnail(0, 0));
    }

    @Test
    void decodeThumbnailWithEmptyImage() {
        instance.setSource(TestUtils.getFixture("empty"));
        assertThrows(SourceFormatException.class, () ->
                instance.readThumbnail(0, 0));
    }

    @Test
    void decodeThumbnailWithInvalidImage() {
        instance.setSource(TestUtils.getFixture("unknown"));
        assertThrows(SourceFormatException.class,
                () -> instance.readThumbnail(0, 0));
    }

    @Test
    void decodeThumbnailWithIllegalImageIndex() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.readThumbnail(1, 0));
    }

    @Test
    void decodeThumbnailWithIllegalThumbnailIndex() {
        instance.setSource(TestUtils.getFixture("iPhone 12.HEIC"));
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.readThumbnail(0, 1));
    }

    @Test
    void decodeThumbnail() throws Exception {
        instance.setSource(TestUtils.getFixture("iPhone 12.HEIC"));
        BufferedImage image = instance.readThumbnail(0, 0);
        assertEquals(320, image.getWidth());
        assertEquals(240, image.getHeight());
        assertEquals(3, image.getSampleModel().getNumBands());
        if (SAVE_IMAGES) TestUtils.save(image);
    }

}
