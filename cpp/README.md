Device key example:

## Install libs

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
python configure.py --amalgamation --single-amalgamation-file --minimized-build --enable-modules=auto_rng,system_rng,sha2_32,ecdsa,ecdh,ecies,chacha,kdf2
```

## Build

```
cd /path/to/this/file
git submodule init
git submodule update
mkdir build
cd build
cmake ..
make -j $(nproc)
```
