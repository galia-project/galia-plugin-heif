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

import is.galia.operation.Encode;

enum ConfigurationKey {

    HEIFENCODER_LOSSLESS(Encode.OPTION_PREFIX + "HEIFEncoder.lossless"),
    HEIFENCODER_PRESET(Encode.OPTION_PREFIX + "HEIFEncoder.preset"),
    HEIFENCODER_QUALITY(Encode.OPTION_PREFIX + "HEIFEncoder.quality"),
    HEIFENCODER_SPEED(Encode.OPTION_PREFIX + "HEIFEncoder.speed");

    private final String key;

    ConfigurationKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    @Override
    public String toString() {
        return key();
    }

}
