package com.github.lambda.common.annotation

/**
 * Kotlin 클래스를 open으로 만들기 위한 어노테이션
 * allOpen 플러그인에서 사용됩니다.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class OpenClass
