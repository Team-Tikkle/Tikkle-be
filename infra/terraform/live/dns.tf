# tikkle.xyz 호스팅 영역. 메일(MX·SPF·DKIM)과 랜딩 페이지 레코드가 함께 있어서
# 영역을 지우면 도메인과 메일이 끊긴다. Terraform은 영역과 api 레코드만 관리하고,
# 나머지 레코드는 건드리지 않는다.
resource "aws_route53_zone" "main" {
  name = "tikkle.xyz"

  # 지금 영역에는 설명이 없다. 공급자 기본값("Managed by Terraform")으로 바뀌지 않게 비워 둔다.
  comment = ""

  lifecycle {
    prevent_destroy = true
  }
}

# 앱 API 주소. 서버의 EIP를 가리킨다.
resource "aws_route53_record" "api" {
  zone_id = aws_route53_zone.main.zone_id
  name    = "api.tikkle.xyz"
  type    = "A"
  ttl     = 300
  records = [aws_eip.api.public_ip]

  lifecycle {
    prevent_destroy = true
  }
}
