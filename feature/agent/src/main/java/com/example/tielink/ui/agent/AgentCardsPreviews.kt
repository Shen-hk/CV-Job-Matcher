package com.example.tielink.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.MaterialTheme
import com.example.tielink.domain.model.GreetingVersion
import com.example.tielink.domain.model.UiCard

@Preview(showBackground = true)
@Composable
private fun MatchCardComposablePreview() {
    MaterialTheme {
        MatchCardComposable(
            card = UiCard.MatchCard(
                overallScore = 85,
                keywordScore = 80,
                experienceScore = 88,
                educationScore = 90,
                skillScore = 78,
                missingSkills = listOf("Kubernetes", "AWS", "Python"),
                highlights = listOf("5年Java开发经验符合要求", "硕士学历匹配", "团队管理经验加分")
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ResumeDiffCardComposablePreview() {
    MaterialTheme {
        ResumeDiffCardComposable(
            card = UiCard.ResumeDiffCard(
                section = "工作经验",
                before = "负责公司内部系统的开发与维护工作",
                after = "主导3个核心业务系统的架构设计与开发，支持日均10000+请求，系统可用性提升至99.9%",
                onAccept = {},
                onRollback = {}
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GreetingCardComposablePreview() {
    MaterialTheme {
        GreetingCardComposable(
            card = UiCard.GreetingCard(
                companyName = "字节跳动",
                position = "高级Android开发工程师",
                greetings = listOf(
                    GreetingVersion(
                        style = "简洁版",
                        content = "尊敬的面试官，您好！我对贵司的高级Android开发工程师岗位非常感兴趣。我有5年Android开发经验，熟练掌握Kotlin和Jetpack Compose，希望能有机会加入贵司。",
                        highlightedSkills = listOf("Kotlin", "Jetpack Compose", "Android")
                    ),
                    GreetingVersion(
                        style = "详细版",
                        content = "尊敬的字节跳动面试官团队：\n\n我是一名拥有5年Android开发经验的工程师，目前正在寻找新的职业机会。在过往的工作中，我主导过多个大型App的架构设计，深度参与过从0到1的产品孵化。\n\n我对贵司的技术氛围和产品矩阵非常向往，期待能有机会与您深入交流。",
                        highlightedSkills = listOf("架构设计", "性能优化", "团队协作")
                    )
                )
            )
        )
    }
}
