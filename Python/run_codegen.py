from grpc_tools import protoc

protoc.main((
    '',
    '--proto_path=../AuthAPI/proto',
    '--python_out=.',
    '--grpc_python_out=.',
    '../AuthAPI/proto/rp.proto',
))
