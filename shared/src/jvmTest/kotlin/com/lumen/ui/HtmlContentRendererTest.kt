package com.lumen.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HtmlContentRendererTest {

    @Test
    fun parseHtmlToBlocks_figureWithTable_producesTableBlock() {
        // Simulates Readability4J output for arXiv-style tables
        val html = """
            <div id="readability-page-1" class="page">
             <article>
              <h2>2 Results</h2>
              <p>Our method achieves state-of-the-art performance.</p>
              <figure id="S5.T1">
               <figcaption>
                <span>TABLE I: </span>Hyperparameters for Training
               </figcaption>
               <table>
                <thead>
                 <tr>
                  <th>Hyperparameter</th>
                  <th>Value</th>
                 </tr>
                </thead>
                <tbody>
                 <tr>
                  <th>Learning Rate</th>
                  <td>0.001</td>
                 </tr>
                 <tr>
                  <th>Batch Size</th>
                  <td>32</td>
                 </tr>
                </tbody>
               </table>
              </figure>
             </article>
            </div>
        """.trimIndent()

        val blocks = parseHtmlToBlocks(html)
        val tables = blocks.filterIsInstance<HtmlBlock.Table>()

        val diag = buildString {
            appendLine("All blocks: ${blocks.map { it::class.simpleName }}")
            for (table in tables) {
                appendLine("Table caption: '${table.caption}'")
                appendLine("Header: ${table.headerRow.map { cells -> cells.map { (it as? InlineElement.Text)?.text } }}")
                appendLine("Rows: ${table.rows.map { row -> row.map { cells -> cells.map { (it as? InlineElement.Text)?.text } } }}")
            }
        }

        assertTrue(tables.isNotEmpty(), "Should parse at least one table from figure+table HTML. $diag")
    }

    @Test
    fun parseHtmlToBlocks_standaloneTable_producesTableBlock() {
        val html = """
            <table>
             <thead>
              <tr><th>Name</th><th>Score</th></tr>
             </thead>
             <tbody>
              <tr><td>Alice</td><td>95</td></tr>
              <tr><td>Bob</td><td>87</td></tr>
             </tbody>
            </table>
        """.trimIndent()

        val blocks = parseHtmlToBlocks(html)
        val tables = blocks.filterIsInstance<HtmlBlock.Table>()

        assertTrue(tables.isNotEmpty(), "Should parse standalone table. Blocks: ${blocks.map { it::class.simpleName }}")
        assertEquals(2, tables[0].headerRow.size, "Header should have 2 cells")
        assertEquals(2, tables[0].rows.size, "Should have 2 data rows")
    }

    @Test
    fun parseHtmlToBlocks_tableWithThInBody_parsesCorrectly() {
        // arXiv uses <th> in tbody for row headers
        val html = """
            <table>
             <thead>
              <tr><th>Param</th><th>Value</th></tr>
             </thead>
             <tbody>
              <tr><th>LR</th><td>0.001</td></tr>
              <tr><th>Batch</th><td>32</td></tr>
             </tbody>
            </table>
        """.trimIndent()

        val blocks = parseHtmlToBlocks(html)
        val tables = blocks.filterIsInstance<HtmlBlock.Table>()

        assertTrue(tables.isNotEmpty(), "Should parse table with th in body rows")
        // The header row should have the thead cells
        assertEquals(2, tables[0].headerRow.size)
        // Body rows should be parsed (th + td mix)
        assertTrue(tables[0].rows.isNotEmpty(), "Body rows should be parsed even with <th> elements")
    }

    @Test
    fun parseHtmlToBlocks_realArxivTableHtml_producesTableBlocks() {
        // Real arXiv table HTML from arxiv.org/html/2603.05504v1 (TABLE I)
        val html = """
            <figure id="A0.T1" class="ltx_table">
            <figcaption class="ltx_caption ltx_centering"><span class="ltx_tag ltx_tag_table"><span class="ltx_text" style="font-size:90%;">TABLE I</span>: </span><span class="ltx_text" style="font-size:90%;">Hyperparameters for Policy Training</span></figcaption>
            <table id="A0.T1.6" class="ltx_tabular ltx_centering ltx_guessed_headers ltx_align_middle">
            <thead class="ltx_thead">
            <tr class="ltx_tr">
            <th class="ltx_td ltx_align_left ltx_th ltx_th_column ltx_th_row"><span class="ltx_text ltx_font_bold">Hyperparameter</span></th>
            <th class="ltx_td ltx_align_left ltx_th ltx_th_column"><span class="ltx_text ltx_font_bold">Value</span></th>
            </tr>
            </thead>
            <tbody class="ltx_tbody">
            <tr class="ltx_tr">
            <th class="ltx_td ltx_align_left ltx_th ltx_th_row">Training Epochs</th>
            <td class="ltx_td ltx_align_left">600</td>
            </tr>
            <tr class="ltx_tr">
            <th class="ltx_td ltx_align_left ltx_th ltx_th_row">Batch Size</th>
            <td class="ltx_td ltx_align_left">64</td>
            </tr>
            <tr class="ltx_tr">
            <th class="ltx_td ltx_align_left ltx_th ltx_th_row">Optimizer</th>
            <td class="ltx_td ltx_align_left">AdamW</td>
            </tr>
            </tbody>
            </table>
            </figure>
        """.trimIndent()

        val blocks = parseHtmlToBlocks(html)
        val tables = blocks.filterIsInstance<HtmlBlock.Table>()

        assertTrue(tables.isNotEmpty(), "Should parse arXiv-style table. Blocks: ${blocks.map { it::class.simpleName }}")
        val table = tables[0]
        assertTrue(table.caption.contains("TABLE I"), "Caption should contain TABLE I, got: '${table.caption}'")
        assertEquals(2, table.headerRow.size, "Header should have 2 cells")
        assertTrue(table.rows.size >= 3, "Should have at least 3 data rows, got: ${table.rows.size}")
    }
}
