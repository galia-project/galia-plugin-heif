# HEIF Plugin for Galia

Provides HEIFDecoder and HEIFEncoder.

See the [HEIF Plugin page on the website](https://galia.is/plugins/heif/)
for more information.

## Development

The native binding to libheif was generated using jextract 22:

```sh
jextract \
    --target-package is.galia.plugin.heif.ffi \
    --output /path/to/src/main/java \
    --include-dir /path/to/include \
    /path/to/include/libheif/heif.h
```

# License

See the file [LICENSE.txt](LICENSE.txt) for license information.
