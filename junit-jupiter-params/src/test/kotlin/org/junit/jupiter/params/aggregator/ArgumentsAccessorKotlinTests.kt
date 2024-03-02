/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */
package org.junit.jupiter.params.aggregator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.platform.commons.util.ReflectionUtils
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.lang.reflect.Method

/**
 * Unit tests for using [ArgumentsAccessor] from Kotlin.
 */
class ArgumentsAccessorKotlinTests {

    @Test
    fun `get() with reified type and index`() {
        assertEquals(1, defaultArgumentsAccessor(1, 1).get<Int>(0))
        assertEquals('A', defaultArgumentsAccessor(1, 'A').get<Char>(0))
    }

    @Test
    fun `get() with reified type and index for incompatible type`() {
        val exception = assertThrows<ArgumentAccessException> {
            defaultArgumentsAccessor(1, Integer.valueOf(1)).get<Char>(0)
        }

        assertThat(exception).hasMessage(
            "Argument at index [0] with value [1] and type [java.lang.Integer] could not be converted or cast to type [java.lang.Character]."
        )
    }

    @Test
    fun `get() with index`() {
        assertEquals(1, defaultArgumentsAccessor(1, 1).get(0))
        assertEquals('A', defaultArgumentsAccessor(1, 'A').get(0))
    }

    @Test
    fun `get() with index and class reference`() {
        assertEquals(1, defaultArgumentsAccessor(1, 1).get(0, Integer::class.java))
        assertEquals('A', defaultArgumentsAccessor(1, 'A').get(0, Character::class.java))
    }

    fun defaultArgumentsAccessor(invocationIndex: Int, vararg arguments: Any): DefaultArgumentsAccessor {
        return DefaultArgumentsAccessor(parameterContext(), invocationIndex, *arguments)
    }

    fun parameterContext(): ParameterContext {
        val declaringExecutable: Method = ReflectionUtils.findMethod(DefaultArgumentsAccessorTests::class.java, "foo").get()
        val parameterContext: ParameterContext = mock()
        `when`(parameterContext.declaringExecutable).thenReturn(declaringExecutable)
        return parameterContext
    }

    fun foo() {
    }
}
