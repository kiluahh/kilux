package com.termux.app.terminal.io;

import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TermuxTerminalExtraKeysTest {

    /**
     * The symbols page is displayed in the same grid as the extra keys, so it must have the same
     * two rows of seven columns, otherwise the toolbar height would no longer fit both pages.
     */
    @Test
    public void testSymbolsMatrixIsTwoRowsOfSeven() throws Exception {
        ExtraKeyButton[][] matrix = TermuxTerminalExtraKeys.buildSymbolsExtraKeysInfo().getMatrix();

        Assert.assertEquals(2, matrix.length);
        Assert.assertEquals(7, matrix[0].length);
        Assert.assertEquals(7, matrix[1].length);
    }

    /**
     * The keys are literal text keys, so the key that is sent to the terminal must be the
     * character that is displayed, including the escape prone backslash.
     */
    @Test
    public void testSymbolsMatrixCharacters() throws Exception {
        ExtraKeyButton[][] matrix = TermuxTerminalExtraKeys.buildSymbolsExtraKeysInfo().getMatrix();

        String[] expected = new String[] {
            "+", "=", "%", "^", "`", "$", "\\",
            "[", "]", "{", "}", "|", "<", ">"
        };

        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {
                String key = expected[row * 7 + col];
                ExtraKeyButton button = matrix[row][col];
                Assert.assertEquals("key at [" + row + "][" + col + "]", key, button.getKey());
                Assert.assertEquals("display at [" + row + "][" + col + "]", key, button.getDisplay());
                Assert.assertFalse("macro at [" + row + "][" + col + "]", button.isMacro());
            }
        }
    }

}
