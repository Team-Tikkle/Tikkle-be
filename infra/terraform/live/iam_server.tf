# API 서버(EC2)의 역할.
# - SSM: Session Manager 접속과 배포 명령(Run Command)을 받는다. 22번 포트를 열지 않는다.
# - ECR: tikkle-be 저장소 하나에서만 이미지를 받는다(옛 구성은 계정 전체 읽기였다).
# - SSM Parameter Store: /tikkle/prod 아래 비밀값만 읽는다. 복호화는 SSM을 거칠 때만.
# CloudWatch 로그·지표 권한은 관측 단계(4-9)에서 더한다.

data "aws_iam_policy_document" "api_server_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "api_server" {
  name               = "tikkle-api-server"
  description        = "Tikkle API server - SSM access, image pull, app parameters"
  assume_role_policy = data.aws_iam_policy_document.api_server_assume.json
}

resource "aws_iam_role_policy_attachment" "api_server_ssm_core" {
  role       = aws_iam_role.api_server.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "api_server" {
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPullAppImage"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [local.ecr_repository_arn]
  }

  statement {
    sid = "ReadAppParameters"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]
    resources = [
      "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.ssm_parameter_path}",
      "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.ssm_parameter_path}/*",
    ]
  }

  statement {
    sid       = "DecryptParametersViaSsmOnly"
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${local.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "api_server" {
  name   = "tikkle-api-server"
  role   = aws_iam_role.api_server.id
  policy = data.aws_iam_policy_document.api_server.json
}

resource "aws_iam_instance_profile" "api_server" {
  name = "tikkle-api-server"
  role = aws_iam_role.api_server.name
}
