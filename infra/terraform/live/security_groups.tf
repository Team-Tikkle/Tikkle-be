# 보안 그룹 설명(description)은 AWS가 영문·일부 기호만 받는다.

# API 서버: 80·443만 연다. 22(SSH)와 8080(앱)은 열지 않는다 — 접속은 SSM, 앱은 nginx 뒤에 있다.
resource "aws_security_group" "api" {
  name        = "tikkle-api"
  description = "Tikkle API server - HTTP and HTTPS from the internet, no SSH"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "tikkle-api"
  }
}

resource "aws_vpc_security_group_ingress_rule" "api_http" {
  security_group_id = aws_security_group.api.id
  description       = "HTTP for certbot challenge and HTTPS redirect"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "api_https" {
  security_group_id = aws_security_group.api.id
  description       = "HTTPS"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

# 나가는 쪽은 열어 둔다: 업비트·Gemini·DeepSeek·CoolSMS·FCM 등 외부 API와 RDS.
resource "aws_vpc_security_group_egress_rule" "api_all" {
  security_group_id = aws_security_group.api.id
  description       = "Outbound to external APIs and RDS"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# DB: API 서버 보안 그룹에서 오는 3306만 받는다. 나가는 규칙은 두지 않는다
# (Terraform은 새 보안 그룹의 기본 나가는 규칙을 지운다).
resource "aws_security_group" "db" {
  name        = "tikkle-db"
  description = "Tikkle RDS MySQL - only from the API server security group"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "tikkle-db"
  }
}

resource "aws_vpc_security_group_ingress_rule" "db_mysql_from_api" {
  security_group_id            = aws_security_group.db.id
  description                  = "MySQL from the API server"
  referenced_security_group_id = aws_security_group.api.id
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
}
