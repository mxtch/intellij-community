/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.intentions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class  PyIntentionTest extends PyTestCase {
  @Nullable private PyDocumentationSettings myDocumentationSettings = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDocumentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    myDocumentationSettings.setFormat(DocStringFormat.REST);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    if (myDocumentationSettings != null) {
      myDocumentationSettings.setFormat(DocStringFormat.PLAIN);
    }
  }

  private void doTest(String hint) {
    myFixture.configureByFile("intentions/before" + getTestName(false) + ".py");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("intentions/after" + getTestName(false) + ".py");
  }

  private void doTest(String hint, LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doTest(hint);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  private void doTest(String hint, boolean ignoreWhiteSpaces) {
    myFixture.configureByFile("intentions/before" + getTestName(false) + ".py");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("intentions/after" + getTestName(false) + ".py", ignoreWhiteSpaces);
  }

  /**
   * Ensures that intention with given hint <i>is not</i> active.
   *
   * @param hint
   */
  private void doNegativeTest(String hint) {
    myFixture.configureByFile("intentions/before" + getTestName(false) + ".py");
    List<IntentionAction> ints = myFixture.filterAvailableIntentions(hint);
    assertEmpty(ints);
  }

  public void testConvertDictComp() {
    doTest(PyBundle.message("INTN.convert.dict.comp.to"), LanguageLevel.PYTHON26);
  }

  public void testConvertSetLiteral() {
    doTest(PyBundle.message("INTN.convert.set.literal.to"), LanguageLevel.PYTHON26);
  }

  public void testReplaceExceptPart() {
    doTest(PyBundle.message("INTN.convert.except.to"), LanguageLevel.PYTHON30);
  }

  public void testConvertBuiltins() {
    doTest(PyBundle.message("INTN.convert.builtin.import"), LanguageLevel.PYTHON30);
  }

  public void testRemoveLeadingU() {
    doTest(PyBundle.message("INTN.remove.leading.$0", "U"), LanguageLevel.PYTHON30);
  }

  public void testRemoveTrailingL() {
    doTest(PyBundle.message("INTN.remove.trailing.l"), LanguageLevel.PYTHON30);
  }

  public void testReplaceOctalNumericLiteral() {
    doTest(PyBundle.message("INTN.replace.octal.numeric.literal"), LanguageLevel.PYTHON30);
  }

  public void testReplaceListComprehensions() {
    doTest(PyBundle.message("INTN.replace.list.comprehensions"), LanguageLevel.PYTHON30);
  }

  public void testReplaceRaiseStatement() {
    doTest(PyBundle.message("INTN.replace.raise.statement"), LanguageLevel.PYTHON30);
  }

  public void testReplaceBackQuoteExpression() {
    doTest(PyBundle.message("INTN.replace.backquote.expression"), LanguageLevel.PYTHON30);
  }

  /*
  public void testReplaceMethod() {
    doTest(PyBundle.message("INTN.replace.method"), LanguageLevel.PYTHON30);
  }
  */

  public void testSplitIf() {
    doTest(PyBundle.message("INTN.split.if.text"));
  }

  public void testNegateComparison() {
    doTest(PyBundle.message("INTN.negate.$0.to.$1", "<=", ">"));
  }

  public void testNegateComparison2() {
    doTest(PyBundle.message("INTN.negate.$0.to.$1", ">", "<="));
  }

  public void testFlipComparison() {
    doTest(PyBundle.message("INTN.flip.$0.to.$1", ">", "<"));
  }

  public void testReplaceListComprehensionWithFor() {
    doTest(PyBundle.message("INTN.replace.list.comprehensions.with.for"));
  }

  public void testReplaceListComprehension2() {    //PY-6731
    doTest(PyBundle.message("INTN.replace.list.comprehensions.with.for"));
  }

  public void testJoinIf() {
    doTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testJoinIfElse() {
    doNegativeTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testJoinIfBinary() {              //PY-4697
    doTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testJoinIfMultiStatements() {           //PY-2970
    doNegativeTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testDictConstructorToLiteralForm() {
    doTest(PyBundle.message("INTN.convert.dict.constructor.to.dict.literal"));
  }

  public void testDictLiteralFormToConstructor() {
    doTest(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
  }

  public void testDictLiteralFormToConstructor1() {      //PY-2873
    myFixture.configureByFile("intentions/beforeDictLiteralFormToConstructor1" + ".py");
    final IntentionAction action = myFixture.getAvailableIntention(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
    assertNull(action);
  }

  public void testDictLiteralFormToConstructor2() {      //PY-5157
    myFixture.configureByFile("intentions/beforeDictLiteralFormToConstructor2" + ".py");
    final IntentionAction action = myFixture.getAvailableIntention(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
    assertNull(action);
  }

  public void testDictLiteralFormToConstructor3() {
    myFixture.configureByFile("intentions/beforeDictLiteralFormToConstructor3" + ".py");
    final IntentionAction action = myFixture.getAvailableIntention(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
    assertNull(action);
  }

  public void testQuotedString() {      //PY-2915
    doTest(PyBundle.message("INTN.quoted.string.double.to.single"));
  }

  public void testQuotedStringDoubleSlash() {      //PY-3295
    doTest(PyBundle.message("INTN.quoted.string.single.to.double"));
  }

  public void testEscapedQuotedString() { //PY-2656
    doTest(PyBundle.message("INTN.quoted.string.single.to.double"));
  }

  public void testDoubledQuotedString() { //PY-2689
    doTest(PyBundle.message("INTN.quoted.string.double.to.single"));
  }

  public void testMultilineQuotedString() { //PY-8064
    getCommonCodeStyleSettings().getIndentOptions().INDENT_SIZE = 2;
    doTest(PyBundle.message("INTN.quoted.string.double.to.single"));
  }

  public void testConvertLambdaToFunction() {
    doTest(PyBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertLambdaToFunction1() {    //PY-6610
    doNegativeTest(PyBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertLambdaToFunction2() {    //PY-6835
    doTest(PyBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertVariadicParam() { //PY-2264
    doTest(PyBundle.message("INTN.convert.variadic.param"));
  }

  public void testConvertTripleQuotedString() { //PY-2697
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedString1() { //PY-7774
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedStringInParenthesized() { //PY-7883
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedUnicodeString() { //PY-7152
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedParenthesizedString() { //PY-7151
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  // PY-8989
  public void testConvertTripleQuotedStringRawStrings() {
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  // PY-8989
  public void testConvertTripleQuotedStringDoesNotReplacePythonEscapes() {
    doTest(PyBundle.message("INTN.triple.quoted.string"), LanguageLevel.PYTHON33);
  }

  // PY-8989
  public void testConvertTripleQuotedStringMultilineGluedString() {
    doTest(PyBundle.message("INTN.triple.quoted.string"), LanguageLevel.PYTHON33);
  }

  public void testConvertTripleQuotedEmptyString() {
    doTest(PyBundle.message("INTN.triple.quoted.string"), LanguageLevel.PYTHON33);
  }

  public void testTransformConditionalExpression() { //PY-3094
    doTest(PyBundle.message("INTN.transform.into.if.else.statement"));
  }

  public void testImportFromToImport() {
    doTest("Convert to 'import sys'");
  }

  // PY-11074
  public void testImportToImportFrom() {
    doTest("Convert to 'from __builtin__ import ...'");
  }

  public void testTypeInDocstring() {
    getCommonCodeStyleSettings().getIndentOptions().INDENT_SIZE = 2;
    doDocReferenceTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring3() {
    doDocReferenceTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring4() {
    doDocReferenceTest(DocStringFormat.REST);
  }

  public void testTypeInDocstringParameterInCallable() {
    doDocReferenceTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring5() {
    getCommonCodeStyleSettings().getIndentOptions().INDENT_SIZE = 2;
    doDocReferenceTest(DocStringFormat.REST);
  }

  public void testTypeInDocstringAtTheEndOfFunction() {
    doDocReturnTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring6() {         //PY-7973
    doNegativeTest(PyBundle.message("INTN.specify.return.type"));
  }

  public void testTypeInDocstring7() {         //PY-8930
    doDocReferenceTest(DocStringFormat.REST);
  }

  // PY-16456
  public void testTypeInDocStringDifferentIndentationSize() {
    doDocReferenceTest(DocStringFormat.REST);
  }

  // PY-16456
  public void testReturnTypeInDocStringDifferentIndentationSize() {
    doDocReturnTypeTest(DocStringFormat.REST);
  }

  public void testReturnTypeInDocstring() {
    doDocReturnTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring1() {
    getCommonCodeStyleSettings().getIndentOptions().INDENT_SIZE = 2;
    doDocReturnTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring2() {
    getCommonCodeStyleSettings().getIndentOptions().INDENT_SIZE = 2;
    doDocReturnTypeTest(DocStringFormat.REST);
  }

  public void testTypeInPy3Annotation() {      //PY-7045
    doTypeAnnotationTest();
  }

  public void testReturnTypeInPy3Annotation() {      //PY-7085
    doTest(PyBundle.message("INTN.specify.return.type.in.annotation"), LanguageLevel.PYTHON32);
  }

  public void testReturnTypeInPy3Annotation1() {      //PY-8783
    doTest(PyBundle.message("INTN.specify.return.type.in.annotation"), LanguageLevel.PYTHON32);
  }

  public void testReturnTypeInPy3Annotation2() {      //PY-8783
    doTest(PyBundle.message("INTN.specify.return.type.in.annotation"), LanguageLevel.PYTHON32);
  }

  public void testTypeAnnotation3() {  //PY-7087
    doTypeAnnotationTest();
  }

  private void doTypeAnnotationTest() {
    doTest(PyBundle.message("INTN.specify.type.in.annotation"), LanguageLevel.PYTHON32);
  }

  public void testTypeAssertion() {
    doTestTypeAssertion();
  }

  public void testTypeAssertion1() { //PY-7089
    doTestTypeAssertion();
  }

  public void testTypeAssertion2() {
    doTestTypeAssertion();
  }

  public void testTypeAssertion3() {                   //PY-7403
    setLanguageLevel(LanguageLevel.PYTHON33);
    try {
      doNegativeTest(PyBundle.message("INTN.insert.assertion"));
    }
    finally {
      setLanguageLevel(null);
    }
  }

  public void testTypeAssertion4() {  //PY-7971
    doTestTypeAssertion();
  }

  public void testTypeAssertionInDictComp() {  //PY-7971
    doNegativeTest(PyBundle.message("INTN.insert.assertion"));
  }

  private void doTestTypeAssertion() {
    doTest(PyBundle.message("INTN.insert.assertion"));
  }

  public void testDocStub() {
    doDocStubTest(DocStringFormat.REST);
  }

  public void testOneLineDocStub() {
    doDocStubTest(DocStringFormat.REST);
  }

  public void testDocStubKeywordOnly() {
    getCommonCodeStyleSettings().getIndentOptions().INDENT_SIZE = 2;
    runWithLanguageLevel(LanguageLevel.PYTHON27, new Runnable() {
      public void run() {
        doDocStubTest(DocStringFormat.REST);
      }
    });
  }

  // PY-9795
  public void testGoogleDocStubInlineFunctionBody() {
    doDocStubTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testGoogleDocStubInlineFunctionBodyMultilineParametersList() {
    doDocStubTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testGoogleDocStubInlineFunctionBodyNoSpaceBefore() {
    doDocStubTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testGoogleDocStubEmptyFunctionBody() {
    doDocStubTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testReturnTypeInNewGoogleDocString() {
    doDocReturnTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testParamTypeInNewGoogleDocString() {
    doDocReferenceTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testGoogleDocStubWithTypes() {
    final PyCodeInsightSettings codeInsightSettings = PyCodeInsightSettings.getInstance();
    final boolean oldInsertTypeDocStub = codeInsightSettings.INSERT_TYPE_DOCSTUB;
    codeInsightSettings.INSERT_TYPE_DOCSTUB = true;
    try {
      doDocStubTest(DocStringFormat.GOOGLE);
    }
    finally {
      codeInsightSettings.INSERT_TYPE_DOCSTUB = oldInsertTypeDocStub;
    }
  }

  // PY-4717
  public void testNumpyDocStub() {
    doDocStubTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testNumpyDocStubWithTypes() {
    final PyCodeInsightSettings codeInsightSettings = PyCodeInsightSettings.getInstance();
    final boolean oldInsertTypeDocStub = codeInsightSettings.INSERT_TYPE_DOCSTUB;
    codeInsightSettings.INSERT_TYPE_DOCSTUB = true;
    try {
      doDocStubTest(DocStringFormat.NUMPY);
    }
    finally {
      codeInsightSettings.INSERT_TYPE_DOCSTUB = oldInsertTypeDocStub;
    }
  }
  
  // PY-4717
  public void testReturnTypeInNewNumpyDocString() {
    doDocReturnTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testParamTypeInNewNumpyDocString() {
    doDocReferenceTest(DocStringFormat.NUMPY);
  }

  // PY-7383
  public void testYieldFrom() {
    doTest(PyBundle.message("INTN.yield.from"), LanguageLevel.PYTHON33);
  }

  public void testConvertStaticMethodToFunction() {
    doTest(PyBundle.message("INTN.convert.static.method.to.function"));
  }

  public void testConvertStaticMethodToFunctionUsage() {
    doTest(PyBundle.message("INTN.convert.static.method.to.function"));
  }

  private void doWithDocStringFormat(@NotNull DocStringFormat format, @NotNull Runnable runnable) {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getModule());
    final DocStringFormat oldFormat = settings.getFormat();
    settings.setFormat(format);
    try {
      runnable.run();
    }
    finally {
      settings.setFormat(oldFormat);
    }
  }

  private void doDocStubTest(@NotNull DocStringFormat format) {
    doWithDocStringFormat(format, new Runnable() {
      @Override
      public void run() {
        CodeInsightSettings.getInstance().JAVADOC_STUB_ON_ENTER = true;
        doTest(PyBundle.message("INTN.doc.string.stub"), true);
      }
    });
  }

  private void doDocReferenceTest(@NotNull DocStringFormat format) {
    doWithDocStringFormat(format, new Runnable() {
      public void run() {
        doTest(PyBundle.message("INTN.specify.type"));
      }
    });
  }

  private void doDocReturnTypeTest(@NotNull DocStringFormat format) {
    doWithDocStringFormat(format, new Runnable() {
        public void run() {
          doTest(PyBundle.message("INTN.specify.return.type"));
        }
      });

  }

}
