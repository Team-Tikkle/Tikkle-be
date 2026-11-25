# 서버의 공인 IP이자, 사용자가 업비트 API 키에 등록하는 허용 IP다.
# 이 주소를 잃으면 같은 IP를 다시 받을 수 없고, 모든 사용자가 업비트 키를 다시 만들어야 한다.
# 그래서 Terraform이 지우거나 교체하지 못하게 막는다.
resource "aws_eip" "api" {
  domain = "vpc"

  tags = {
    Name = "tikkle-api"
  }

  lifecycle {
    prevent_destroy = true
  }
}
