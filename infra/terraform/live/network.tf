# 전용 VPC. 기본 VPC(172.31.0.0/16)는 쓰지 않고 그대로 둔다.
#
# 퍼블릭 서브넷: 서버. 서버에 EIP를 직접 붙여 업비트로 나가는 IP를 15.164.79.198 하나로 유지한다.
# 프라이빗 서브넷: RDS 서브넷 그룹용. 밖으로 나갈 일이 없어 NAT를 두지 않는다.
# 서버를 여러 대로 늘릴 때는 서버를 프라이빗 서브넷으로 옮기고 NAT에 EIP를 붙인다(나가는 IP는 그대로 하나).

locals {
  subnets = {
    public = {
      a = { az = "ap-northeast-2a", cidr = "10.10.0.0/24" }
      c = { az = "ap-northeast-2c", cidr = "10.10.1.0/24" }
    }
    private = {
      a = { az = "ap-northeast-2a", cidr = "10.10.10.0/24" }
      c = { az = "ap-northeast-2c", cidr = "10.10.11.0/24" }
    }
  }
}

resource "aws_vpc" "main" {
  cidr_block           = "10.10.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "tikkle"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "tikkle"
  }
}

resource "aws_subnet" "public" {
  for_each = local.subnets.public

  vpc_id            = aws_vpc.main.id
  availability_zone = each.value.az
  cidr_block        = each.value.cidr

  # 서버가 EIP를 붙이기 전, 부팅 직후 패키지를 받으려면 공인 IP가 필요하다.
  # EIP를 붙이면 자동으로 받은 공인 IP는 반납된다.
  map_public_ip_on_launch = true

  tags = {
    Name = "tikkle-public-${each.key}"
    Tier = "public"
  }
}

resource "aws_subnet" "private" {
  for_each = local.subnets.private

  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.value.az
  cidr_block              = each.value.cidr
  map_public_ip_on_launch = false

  tags = {
    Name = "tikkle-private-${each.key}"
    Tier = "private"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "tikkle-public"
  }
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

# VPC 안 경로(local)만 있다.
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "tikkle-private"
  }
}

resource "aws_route_table_association" "private" {
  for_each = aws_subnet.private

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private.id
}

# VPC를 만들면 AWS가 같은 그룹끼리 모두 허용하는 기본 보안 그룹을 만든다.
# 아무것도 이 그룹을 쓰지 않도록 규칙을 모두 비운다.
resource "aws_default_security_group" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "tikkle-default-unused"
  }
}
