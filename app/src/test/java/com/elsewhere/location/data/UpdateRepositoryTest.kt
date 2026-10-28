package com.elsewhere.location.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateRepositoryTest {

    @Test
    fun `compares versions with different segment counts`() {
        assertThat(UpdateRepository.compareVersions("1.2.0", "1.1.9")).isGreaterThan(0)
        assertThat(UpdateRepository.compareVersions("v1.10.0", "1.9.9")).isGreaterThan(0)
        assertThat(UpdateRepository.compareVersions("2.0", "2.0.0")).isEqualTo(0)
        assertThat(UpdateRepository.compareVersions("1.0.0-dev", "1.0.0")).isEqualTo(0)
        assertThat(UpdateRepository.compareVersions("0.9.9", "1.0")).isLessThan(0)
    }
}
