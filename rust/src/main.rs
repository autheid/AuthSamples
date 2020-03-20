extern crate simple_error;

pub mod rp {
    tonic::include_proto!("autheid.rp");
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let tls = tonic::transport::ClientTlsConfig::with_rustls()
        .domain_name("api.staging.autheid.com")
        .clone();

    let channel =
        tonic::transport::Channel::from_static("https://api.staging.autheid.com")
            .tls_config(&tls)
            .intercept_headers(|headers| {
                headers.insert(
                    "Authorization",
                    http::header::HeaderValue::from_static("Bearer Pj+GIg2/l7ZKmicZi37+1giqKJ1WH3Vt8vSSxCuvPkKD"),
                );
            })
            .clone();

    let mut client = rp::client::RequestsClient::new(channel.connect().await?);

    let create_request = rp::CreateRequest {
        r#type: rp::RequestType::Kyc as i32,
        title: "Test".into(),
        email: "test21@verified-fast.com".into(),
        ..Default::default()
    };

    let create_response = client.create(tonic::Request::new(create_request)).await?.into_inner();

    if !create_response.success {
        let err: Box<dyn std::error::Error> = Box::new(simple_error::SimpleError::new("unexpected response"));
        return Err(err)
    }

    println!("create response: {:?}", create_response);

    let get_result_request = rp::GetResultRequest {
        request_id: create_response.request_id
    };

    let get_result_response = client.get_result(tonic::Request::new(get_result_request)).await?.into_inner();

    println!("result response: {:?}", get_result_response);

    Ok(())
}
