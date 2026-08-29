package com.example.healthheatv2
import org.junit.Test
import com.google.mlkit.genai.prompt.GenerateContentResponse
class ReflectionTest {
  @Test fun testReflection() {
    println("METHODS:")
    GenerateContentResponse::class.java.methods.forEach { println(it.name) }
    println("CANDIDATE METHODS:")
    val listType = GenerateContentResponse::class.java.methods.find { it.name == "getCandidates" }?.returnType
    // wait listType is just List, we need generic type
    val genType = GenerateContentResponse::class.java.methods.find { it.name == "getCandidates" }?.genericReturnType as java.lang.reflect.ParameterizedType
    val candClass = genType.actualTypeArguments[0] as Class<*>
    candClass.methods.forEach { println(it.name) }
  }
}
