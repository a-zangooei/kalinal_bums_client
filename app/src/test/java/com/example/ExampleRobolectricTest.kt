package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleRobolectricTest {

  @Test
  fun testAppNameResourceConfigured() {
    val candidates = listOf(
      File("app/src/main/res/values/strings.xml"),
      File("src/main/res/values/strings.xml"),
      File("res/values/strings.xml")
    )
    val file = candidates.find { it.exists() }
    assertTrue("strings.xml must exist in resource directory", file != null)

    val content = file!!.readText()
    assertTrue("app_name must be defined as کالینان", content.contains("<string name=\"app_name\">کالینان</string>"))
  }
}
