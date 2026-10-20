package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.terminal.CandidateItem
import com.example.ui.terminal.CandidateType
import com.example.ui.terminal.CommandCompletionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandCompletionEngineTest {

  @Test
  fun testAgyInQuickSuggestions() {
    // 1. Partial command "ag" should suggest "agy"
    val agSuggestions = CommandCompletionEngine.getQuickSuggestions("ag")
    assertTrue(agSuggestions.any { it.text == "agy" && it.type == CandidateType.COMMAND })

    // 2. Exact command "agy" should suggest agy subcommands and flags
    val agySuggestions = CommandCompletionEngine.getQuickSuggestions("agy")
    assertTrue(agySuggestions.any { it.text == "--dangerously-skip-permissions" })
    assertTrue(agySuggestions.any { it.text == "-c" })
    assertTrue(agySuggestions.any { it.text == "--mode auto" })
    assertTrue(agySuggestions.any { it.text == "models" })
    assertTrue(agySuggestions.any { it.text == "mcp" })

    // 3. Command with space "agy " should suggest subcommands
    val agySpaceSuggestions = CommandCompletionEngine.getQuickSuggestions("agy ")
    assertTrue(agySpaceSuggestions.any { it.text == "--dangerously-skip-permissions" })
    assertTrue(agySpaceSuggestions.any { it.text == "plugin" })

    // 4. Command with flag prefix "agy -" should filter for flags
    val agyFlagSuggestions = CommandCompletionEngine.getQuickSuggestions("agy -")
    assertTrue(agyFlagSuggestions.all { it.text.startsWith("-") })
    assertTrue(agyFlagSuggestions.any { it.text == "--dangerously-skip-permissions" })
    assertTrue(agyFlagSuggestions.any { it.text == "-c" })

    // 5. Nested command "agy mcp " should suggest mcp subcommands
    val mcpSuggestions = CommandCompletionEngine.getQuickSuggestions("agy mcp ")
    assertTrue(mcpSuggestions.any { it.text == "list" })
    assertTrue(mcpSuggestions.any { it.text == "add" })
    assertTrue(mcpSuggestions.any { it.text == "remove" })
  }

  @Test
  fun testAgyCompleteTab() {
    // Exact command "agy" with Tab: should complete to "agy " and present subcommands in candidates
    val tfv = TextFieldValue("agy", TextRange(3))
    val result = CommandCompletionEngine.complete(tfv)

    assertEquals("agy ", result.newTextFieldValue.text)
    assertEquals(4, result.newTextFieldValue.selection.end)
    assertTrue("Candidates should contain agy subcommands/options", result.candidates.isNotEmpty())
    assertTrue(result.candidates.any { it.text == "--dangerously-skip-permissions" })
  }

  @Test
  fun testAgySubcommandCycle() {
    val tfv = TextFieldValue("agy ", TextRange(4))
    val resultCycle0 = CommandCompletionEngine.complete(tfv, cycleIndex = 0)
    assertTrue(resultCycle0.newTextFieldValue.text.startsWith("agy --dangerously-skip-permissions"))

    val resultCycle1 = CommandCompletionEngine.complete(tfv, cycleIndex = 1)
    assertTrue(resultCycle1.newTextFieldValue.text.startsWith("agy -c"))
  }

  @Test
  fun testApplyCandidatePreservesCommand() {
    // If user typed "agy" without trailing space and clicks "--dangerously-skip-permissions" chip
    val tfv = TextFieldValue("agy", TextRange(3))
    val candidate = CandidateItem("--dangerously-skip-permissions", "--dangerously-skip-permissions", CandidateType.OPTION)

    val updated = CommandCompletionEngine.applyCandidate(tfv, candidate)
    assertEquals("agy --dangerously-skip-permissions ", updated.text)
    assertEquals("agy --dangerously-skip-permissions ".length, updated.selection.end)
  }

  @Test
  fun testApplyCandidateWithTrailingSpace() {
    val tfv = TextFieldValue("agy ", TextRange(4))
    val candidate = CandidateItem("-c", "-c", CandidateType.OPTION)

    val updated = CommandCompletionEngine.applyCandidate(tfv, candidate)
    assertEquals("agy -c ", updated.text)
  }
}
