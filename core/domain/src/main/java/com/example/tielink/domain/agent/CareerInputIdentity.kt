package com.example.tielink.domain.agent

import com.example.tielink.domain.model.CareerTaskInputs
import java.security.MessageDigest

object CareerInputIdentity {
    fun snapshot(jdId: Long?, jdText: String?, resumeId: Long?, resumeText: String?): CareerTaskInputs = CareerTaskInputs(
        jdId, fingerprint(jdText.orEmpty()), resumeId, fingerprint(resumeText.orEmpty())
    )

    fun fingerprint(text: String): String = if (text.isBlank()) "" else
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
