# Terraform 이전부터 있던 자원을 상태로 가져온다.
# 가져온 뒤에도 남겨 두면 아무 일도 하지 않는다(이미 상태에 있으면 무시된다).
import {
  to = aws_eip.api
  id = "eipalloc-02cec70cf21aa91f7"
}

import {
  to = aws_route53_zone.main
  id = "Z0712434KT7AJSJ8UE8C"
}

import {
  to = aws_route53_record.api
  id = "Z0712434KT7AJSJ8UE8C_api.tikkle.xyz_A"
}

# 콘솔에서 만든 월 예산. 가져오기 ID는 "계정 번호:예산 이름"이라 계정 번호를 코드에 적지 않고 읽어 온다.
import {
  to = aws_budgets_budget.monthly
  id = "${local.account_id}:My Monthly Cost Budget"
}
