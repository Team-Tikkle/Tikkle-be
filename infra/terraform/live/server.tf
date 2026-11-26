# API 서버 1대 (출시 구성 0 — 스케줄러와 SSE가 단일 인스턴스를 전제로 한다).
# 접속과 배포 명령은 SSM으로만 한다. 키 페어 없음, 22번 포트 없음.

locals {
  # Amazon Linux 2023 arm64. 2026-11-21 이미지
  # (공개 SSM 파라미터 /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64).
  # 바꿀 때는 이 값을 고치고 서버를 명시적으로 교체한다(terraform apply -replace=aws_instance.api).
  api_ami_id = "ami-0c752e0d8198a3081"

  api_domain    = "api.tikkle.xyz"
  certbot_email = "support@tikkle.xyz"
}

resource "aws_instance" "api" {
  ami                    = local.api_ami_id
  instance_type          = "t4g.medium"
  subnet_id              = aws_subnet.public["a"].id
  vpc_security_group_ids = [aws_security_group.api.id]
  iam_instance_profile   = aws_iam_instance_profile.api_server.name

  # 첫 부팅 때 한 번 도는 준비 스크립트. 비밀값은 넣지 않는다(콘솔·메타데이터에서 보인다).
  # Windows에서 체크아웃하면 줄바꿈이 CRLF가 될 수 있다. bash는 \r 때문에 깨지므로 모두 지운다.
  user_data = replace(templatefile("${path.module}/server_user_data.sh.tftpl", {
    domain        = local.api_domain
    eip           = aws_eip.api.public_ip
    certbot_email = local.certbot_email
    nginx_conf    = file("${path.module}/../../nginx/tikkle.conf")
  }), "\r", "")

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 30
    encrypted             = true
    delete_on_termination = true
  }

  # IMDSv2만. hop 제한 1이라 컨테이너(앱·Redis)는 인스턴스 역할 자격 증명을 꺼낼 수 없다.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  # 하드웨어 장애 때 같은 인스턴스 ID·EIP로 복구된다.
  maintenance_options {
    auto_recovery = "default"
  }

  tags = {
    Name = "tikkle-api"
    Role = "api" # 배포 역할(iam_github.tf)은 이 태그가 붙은 서버에만 명령을 보낼 수 있다
  }

  # AMI나 준비 스크립트를 바꿔도 plan이 서버를 몰래 갈아 끼우지 않게 한다.
  # 바꾼 것을 반영하려면 사람이 -replace 로 교체한다.
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

# 업비트 허용 IP를 서버에 붙인다. 서버를 바꿔도 EIP(eip.tf, prevent_destroy)는 남고 연결만 다시 만든다.
resource "aws_eip_association" "api" {
  instance_id   = aws_instance.api.id
  allocation_id = aws_eip.api.id
}
