# Device key C++ example

## Download

### gRPC

```
cd /path/to/this/file
git clone https://github.com/grpc/grpc
cd grpc
git checkout v1.19.1
git submodule init
git submodule update
```

### Botan (required for device keys decryption)

```
cd /path/to/this/file
git clone https://github.com/randombit/botan
cd botan
git checkout 2.10.0
python configure.py --amalgamation --single-amalgamation-file --minimized-build --enable-modules=auto_rng,system_rng,sha2_32,ecdsa,ecdh,ecies,chacha,kdf2 --disable-shared-library
```

## Build

### Common build prerequisites:

- CMake
- Go (required for gRPC SSL build)
- Perl (required for gRPC SSL build)
- Python (required for Botan build)

### Linux/macOS:

```
cd /path/to/this/file
git submodule init
git submodule update
mkdir build
cd build
cmake ..
make -j $(nproc)
```


### Windows:

Download and install:

- MSVC (Tested with MSVC 2017)

```
cd c:\path\to\this\file
git submodule init
git submodule update
mkdir build
cd build
cmake .. -A x64
cmake --build ./
```

Windows build should be made in MSVC "x64 Native Tools Command Prompt"

Please set `GRPC_DEFAULT_SSL_ROOTS_FILE_PATH` env variable on Windows before starting the test app.

For example:

```
set GRPC_DEFAULT_SSL_ROOTS_FILE_PATH=c:\path\to\this\file\grpc\etc\roots.pem
```
