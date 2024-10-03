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

import is.galia.image.Format;
import is.galia.image.MediaType;

import java.util.List;
import java.util.Set;

final class Formats {

    static final Format AVIF = new Format(
            "avif",
                    "AVIF",
            List.of(new MediaType("image", "avif")),
            List.of("avif"),
            true,
            false,
            true);

    static final Format HEIF = new Format(
            "heif",
            "HEIF",
            List.of(new MediaType("image", "heif"),
                    new MediaType("image", "heif-sequence"),
                    new MediaType("image", "heic"),
                    new MediaType("image", "heic-sequence"),
                    new MediaType("image", "avif")),
            List.of("heif", "heifs", "heic", "heics", "avci", "avcs", "hif"),
            true,
            false,
            true);

    static Set<Format> all() {
        return Set.of(AVIF, HEIF);
    }

    private Formats() {}

}
