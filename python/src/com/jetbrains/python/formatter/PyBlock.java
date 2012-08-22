package com.jetbrains.python.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * @author yole
 */
public class PyBlock implements ASTBlock {
  private final Alignment _alignment;
  private final Indent _indent;
  private final ASTNode _node;
  private final Wrap _wrap;
  private final PyBlockContext myContext;
  private List<PyBlock> _subBlocks = null;
  private Alignment myChildAlignment;
  private static final boolean DUMP_FORMATTING_BLOCKS = false;

  private static final TokenSet ourListElementTypes = TokenSet.create(PyElementTypes.LIST_LITERAL_EXPRESSION,
                                                                      PyElementTypes.LIST_COMP_EXPRESSION,
                                                                      PyElementTypes.DICT_COMP_EXPRESSION,
                                                                      PyElementTypes.SET_COMP_EXPRESSION,
                                                                      PyElementTypes.DICT_LITERAL_EXPRESSION,
                                                                      PyElementTypes.SET_LITERAL_EXPRESSION,
                                                                      PyElementTypes.ARGUMENT_LIST,
                                                                      PyElementTypes.PARAMETER_LIST,
                                                                      PyElementTypes.TUPLE_EXPRESSION,
                                                                      PyElementTypes.PARENTHESIZED_EXPRESSION,
                                                                      PyElementTypes.SLICE_EXPRESSION,
                                                                      PyElementTypes.SUBSCRIPTION_EXPRESSION);

  private static final TokenSet ourBrackets = TokenSet.create(PyTokenTypes.LPAR, PyTokenTypes.RPAR,
                                                              PyTokenTypes.LBRACE, PyTokenTypes.RBRACE,
                                                              PyTokenTypes.LBRACKET, PyTokenTypes.RBRACKET);

  public PyBlock(final ASTNode node,
                 final Alignment alignment,
                 final Indent indent,
                 final Wrap wrap,
                 final PyBlockContext context) {
    _alignment = alignment;
    _indent = indent;
    _node = node;
    _wrap = wrap;
    myContext = context;
  }

  @NotNull
  public ASTNode getNode() {
    return _node;
  }

  @NotNull
  public TextRange getTextRange() {
    return _node.getTextRange();
  }

  private Alignment getAlignmentForChildren() {
    if (myChildAlignment == null) {
      myChildAlignment = Alignment.createAlignment();
    }
    return myChildAlignment;
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (_subBlocks == null) {
      _subBlocks = buildSubBlocks();
      if (DUMP_FORMATTING_BLOCKS) {
        dumpSubBlocks();
      }
    }
    return new ArrayList<Block>(_subBlocks);
  }

  private List<PyBlock> buildSubBlocks() {
    List<PyBlock> blocks = new ArrayList<PyBlock>();
    for (ASTNode child = _node.getFirstChildNode(); child != null; child = child.getTreeNext()) {

      IElementType childType = child.getElementType();

      if (child.getTextRange().getLength() == 0) continue;

      if (childType == TokenType.WHITE_SPACE) {
        continue;
      }

      blocks.add(buildSubBlock(child));
    }
    return Collections.unmodifiableList(blocks);
  }

  private PyBlock buildSubBlock(ASTNode child) {
    IElementType parentType = _node.getElementType();
    IElementType grandparentType = _node.getTreeParent() == null ? null : _node.getTreeParent().getElementType();
    IElementType childType = child.getElementType();
    Wrap wrap = null;
    Indent childIndent = Indent.getNoneIndent();
    Alignment childAlignment = null;
    if (childType == PyElementTypes.STATEMENT_LIST || childType == PyElementTypes.IMPORT_ELEMENT) {
      if (hasLineBreaksBefore(child, 1)) {
        childIndent = Indent.getNormalIndent();
      }
    }
    if (ourListElementTypes.contains(parentType)) {
      // wrapping in non-parenthesized tuple expression is not allowed (PY-1792)
      if ((parentType != PyElementTypes.TUPLE_EXPRESSION || grandparentType == PyElementTypes.PARENTHESIZED_EXPRESSION) &&
          !ourBrackets.contains(childType)) {
        wrap = Wrap.createWrap(WrapType.NORMAL, true);
      }
      if (needListAlignment(child) && !isEmptyList(_node.getPsi())) {
        childAlignment = getAlignmentForChildren();
      }
    }
    else if (parentType == PyElementTypes.BINARY_EXPRESSION &&
             PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens().contains(childType)) {
      childAlignment = getAlignmentForChildren();
    }

    if (parentType == PyElementTypes.LIST_LITERAL_EXPRESSION) {
      if (childType == PyTokenTypes.RBRACKET || childType == PyTokenTypes.LBRACKET) {
        childIndent = Indent.getNoneIndent();
      }
      else {
        childIndent = Indent.getNormalIndent();
      }
    }
    else if (parentType == PyElementTypes.ARGUMENT_LIST || parentType == PyElementTypes.PARAMETER_LIST) {
      if (childType == PyTokenTypes.RPAR) {
        childIndent = Indent.getNoneIndent();
      }
      else {
        childIndent = parentType == PyElementTypes.PARAMETER_LIST 
                      ? Indent.getContinuationIndent()
                      : Indent.getNormalIndent();
      }
    }
    else if (parentType == PyElementTypes.DICT_LITERAL_EXPRESSION || parentType == PyElementTypes.SET_LITERAL_EXPRESSION) {
      if (childType == PyTokenTypes.RBRACE || !hasLineBreaksBefore(child, 1)) {
        childIndent = Indent.getNoneIndent();
      }
      else {
        childIndent = Indent.getNormalIndent();
      }
    }
    else if (parentType == PyElementTypes.STRING_LITERAL_EXPRESSION) {
      if (PyTokenTypes.STRING_NODES.contains(childType)) {
        childAlignment = getAlignmentForChildren();
      }
    }
    else if (parentType == PyElementTypes.FROM_IMPORT_STATEMENT) {
      if ((childType == PyElementTypes.IMPORT_ELEMENT || childType == PyTokenTypes.RPAR) &&
          _node.findChildByType(PyTokenTypes.LPAR) != null) {
        childAlignment = getAlignmentForChildren();
      }
    }
    else if (parentType == PyElementTypes.KEY_VALUE_EXPRESSION) {
      PyKeyValueExpression keyValue = (PyKeyValueExpression) _node.getPsi();
      if (keyValue != null && child.getPsi() == keyValue.getValue()) {
        childIndent = Indent.getNormalIndent();
      }
    }
    else if ((parentType == PyElementTypes.PARENTHESIZED_EXPRESSION || parentType == PyElementTypes.GENERATOR_EXPRESSION) &&
             hasLineBreaksBefore(child, 1)) {
      childIndent = Indent.getNormalIndent();
    }

    if (isAfterStatementList(child) && !hasLineBreaksBefore(child, 2)) {  // maybe enter was pressed and cut us from a previous (nested) statement list
      childIndent = Indent.getNormalIndent();
    }

    return new PyBlock(child, childAlignment, childIndent, wrap, myContext);
  }

  private static boolean isEmptyList(PsiElement psi) {
    if (psi instanceof PyDictLiteralExpression) {
      return ((PyDictLiteralExpression) psi).getElements().length == 0;
    }
    if (psi instanceof PySequenceExpression) {
      return ((PySequenceExpression) psi).getElements().length == 0;
    }
    return false;
  }

  private static boolean isAfterStatementList(ASTNode child) {
    try {
      PsiElement prev = sure(child.getPsi().getPrevSibling());
      sure(prev instanceof PyStatement);
      PsiElement lastchild = PsiTreeUtil.getDeepestLast(prev);
      sure(lastchild.getParent() instanceof PyStatementList);
      return true;
    }
    catch (IncorrectOperationException e) {
      // not our cup of tea
      return false;
    }
  }

  private boolean needListAlignment(ASTNode child) {
    IElementType childType = child.getElementType();
    ASTNode firstGrandchild = child.getFirstChildNode();
    IElementType firstGrandchildType = firstGrandchild == null ? null : firstGrandchild.getElementType();
    if (PyTokenTypes.OPEN_BRACES.contains(childType)) {
      return false;
    }
    if (PyTokenTypes.OPEN_BRACES.contains(firstGrandchildType)) {
      PsiElement psi = child.getPsi();
      if (psi instanceof PySequenceExpression && ((PySequenceExpression)psi).getElements().length == 0) {
        return false;
      }
    }
    if (PyTokenTypes.CLOSE_BRACES.contains(childType)) {
      ASTNode prevNonSpace = findPrevNonSpaceNode(child);
      if (prevNonSpace != null && prevNonSpace.getElementType() == PyTokenTypes.COMMA && myContext.getMode() == FormattingMode.ADJUST_INDENT) {
        return true;
      }
      return false;
    }
    if (_node.getElementType() == PyElementTypes.ARGUMENT_LIST) {
      if (!myContext.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS) {
        return false;
      }
      if (child.getElementType() == PyTokenTypes.COMMA) {
        return false;
      }
      PyArgumentList argList = (PyArgumentList)_node.getPsi();
      if (argList != null) {
        PyExpression[] arguments = argList.getArguments();
        return arguments.length > 1 || hasLineBreaksBefore(child, 1) || (
          arguments.length == 1 && PyPsiUtils.getNextComma(arguments[0].getNode()) != null);
      }
      return false;
    }
    if (_node.getElementType() == PyElementTypes.PARAMETER_LIST) {
      return myContext.getSettings().ALIGN_MULTILINE_PARAMETERS;
    }
    if (child.getElementType() == PyTokenTypes.COMMA) {
      return false;
    }
    return true;
  }

  @Nullable
  private static ASTNode findPrevNonSpaceNode(ASTNode node) {
    do {
      node = node.getTreePrev();
    } while(node != null && (node.getElementType() == TokenType.WHITE_SPACE || PyTokenTypes.WHITESPACE.contains(node.getElementType())));
    return node;
  }

  private static boolean hasLineBreaksBefore(ASTNode child, int minCount) {
    final ASTNode treePrev = child.getTreePrev();
    return (treePrev != null && isWhitespaceWithLineBreaks(TreeUtil.findLastLeaf(treePrev), minCount)) ||
           isWhitespaceWithLineBreaks(child.getFirstChildNode(), minCount);
  }

  private static boolean isWhitespaceWithLineBreaks(ASTNode node, int minCount) {
    if (node != null && node.getElementType() == TokenType.WHITE_SPACE) {
      String prevNodeText = node.getText();
      int count = 0;
      for(int i=0; i<prevNodeText.length(); i++) {
        if (prevNodeText.charAt(i) == '\n') {
          count++;
          if (count == minCount) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void dumpSubBlocks() {
    System.out.println("Subblocks of " + _node.getPsi() + ":");
    for (Block block : _subBlocks) {
      if (block instanceof PyBlock) {
        System.out.println("  " + ((PyBlock)block).getNode().getPsi().toString() + " " + block.getTextRange().getStartOffset() + ":" + block
          .getTextRange().getLength());
      }
      else {
        System.out.println("  <unknown block>");
      }
    }
  }

  @Nullable
  public Wrap getWrap() {
    return _wrap;
  }

  @Nullable
  public Indent getIndent() {
    assert _indent != null;
    return _indent;
  }

  @Nullable
  public Alignment getAlignment() {
    return _alignment;
  }

  @Nullable
  public Spacing getSpacing(Block child1, Block child2) {
    return myContext.getSpacingBuilder().getSpacing(this, child1, child2);
  }

  @NotNull
  public ChildAttributes getChildAttributes(int newChildIndex) {
    int statementListsBelow = 0;
    if (newChildIndex > 0) {
      // always pass decision to a sane block from top level from file or definition
      if (_node.getPsi() instanceof PyFile || _node.getElementType() == PyTokenTypes.COLON) {
        return ChildAttributes.DELEGATE_TO_PREV_CHILD;
      }

      PyBlock insertAfterBlock = _subBlocks.get(newChildIndex - 1);

      ASTNode prevNode = insertAfterBlock.getNode();
      PsiElement prevElt = prevNode.getPsi();

      // stmt lists, parts and definitions should also think for themselves
      if (prevElt instanceof PyStatementList) {
        if (dedentAfterLastStatement((PyStatementList)prevElt)) {
          return new ChildAttributes(Indent.getNoneIndent(), getChildAlignment());
        }
        return ChildAttributes.DELEGATE_TO_PREV_CHILD;
      }
      else if (prevElt instanceof PyStatementPart) {
        return ChildAttributes.DELEGATE_TO_PREV_CHILD;
      }

      ASTNode lastChild = insertAfterBlock.getNode();

      // HACK? This code fragment is needed to make testClass2() pass,
      // but I don't quite understand why it is necessary and why the formatter
      // doesn't request childAttributes from the correct block
      while (lastChild != null) {
        IElementType last_type = lastChild.getElementType();
        if (last_type == PyElementTypes.STATEMENT_LIST && hasLineBreaksBefore(lastChild, 1)) {
          if (dedentAfterLastStatement((PyStatementList)lastChild.getPsi())) {
            break;
          }
          statementListsBelow++;
        }
        else if (statementListsBelow > 0 && lastChild.getPsi() instanceof PsiErrorElement) {
          statementListsBelow++;
        }
        if (_node.getElementType() == PyElementTypes.STATEMENT_LIST && lastChild.getPsi() instanceof PsiErrorElement) {
          return ChildAttributes.DELEGATE_TO_PREV_CHILD;
        }
        lastChild = getLastNonSpaceChild(lastChild, true);
      }
    }

    // HACKETY-HACK
    // If a multi-step dedent follows the cursor position (see testMultiDedent()),
    // the whitespace (which must be a single Py:LINE_BREAK token) gets attached
    // to the outermost indented block (because we may not consume the DEDENT
    // tokens while parsing inner blocks). The policy is to put the indent to
    // the innermost block, so we need to resolve the situation here. Nested
    // delegation sometimes causes NPEs in formatter core, so we calculate the
    // correct indent manually.
    if (statementListsBelow > 0) { // was 1... strange
      @SuppressWarnings("ConstantConditions")
      int indent = myContext.getSettings().getIndentOptions().INDENT_SIZE;
      return new ChildAttributes(Indent.getSpaceIndent(indent * statementListsBelow), null);
    }

    /*
    // it might be something like "def foo(): # comment" or "[1, # comment"; jump up to the real thing
    if (_node instanceof PsiComment || _node instanceof PsiWhiteSpace) {
      get
    }
    */


    Indent childIndent = getChildIndent(newChildIndex);
    Alignment childAlignment = getChildAlignment();
    return new ChildAttributes(childIndent, childAlignment);
  }

  private static boolean dedentAfterLastStatement(PyStatementList statementList) {
    final PyStatement[] statements = statementList.getStatements();
    if (statements.length == 0) {
      return false;
    }
    PyStatement last = statements[statements.length - 1];
    return last instanceof PyReturnStatement || last instanceof PyRaiseStatement || last instanceof PyPassStatement;
  }

  @Nullable
  private Alignment getChildAlignment() {
    if (ourListElementTypes.contains(_node.getElementType())) {
      if (_node.getPsi() instanceof PyParameterList && !myContext.getSettings().ALIGN_MULTILINE_PARAMETERS) {
        return null;
      }
      if (_node.getPsi() instanceof PyDictLiteralExpression) {
        PyKeyValueExpression[] elements = ((PyDictLiteralExpression)_node.getPsi()).getElements();
        if (elements.length == 0) {
          return null;
        }
        PyKeyValueExpression last = elements[elements.length-1];
        if (last.getValue() == null) { // incomplete
          return null;
        }
      }
      return getAlignmentForChildren();
    }
    return null;
  }

  private Indent getChildIndent(int newChildIndex) {
    ASTNode afterNode = getAfterNode(newChildIndex);
    ASTNode lastChild = getLastNonSpaceChild(_node, false);
    if (lastChild != null && lastChild.getElementType() == PyElementTypes.STATEMENT_LIST && _subBlocks.size() >= newChildIndex) {
      if (afterNode == null) {
        return Indent.getNoneIndent();
      }

      // handle pressing Enter after colon and before first statement in
      // existing statement list
      if (afterNode.getElementType() == PyElementTypes.STATEMENT_LIST || afterNode.getElementType() == PyTokenTypes.COLON) {
        return Indent.getNormalIndent();
      }

      // handle pressing Enter after colon when there is nothing in the
      // statement list
      ASTNode lastFirstChild = lastChild.getFirstChildNode();
      if (lastFirstChild != null && lastFirstChild == lastChild.getLastChildNode() && lastFirstChild.getPsi() instanceof PsiErrorElement) {
        return Indent.getNormalIndent();
      }
    }
    else if (lastChild != null && PyElementTypes.LIST_LIKE_EXPRESSIONS.contains(lastChild.getElementType())) {
      // handle pressing enter at the end of a list literal when there's no closing paren or bracket 
      ASTNode lastLastChild = lastChild.getLastChildNode();
      if (lastLastChild != null && lastLastChild.getPsi() instanceof PsiErrorElement) {
        // we're at a place like this: [foo, ... bar, <caret>
        // we'd rather align to foo. this may be not a multiple of tabs.
        PsiElement expr = lastChild.getPsi();
        PsiElement exprItem = expr.getFirstChild();
        boolean found = false;
        while (exprItem != null) { // find a worthy element to align to
          if (exprItem instanceof PyElement) {
            found = true; // align to foo in "[foo,"
            break;
          }
          if (exprItem instanceof PsiComment) {
            found = true; // align to foo in "[ # foo,"
            break;
          }
          exprItem = exprItem.getNextSibling();
        }
        if (found) {
          PsiDocumentManager docMgr = PsiDocumentManager.getInstance(exprItem.getProject());
          Document doc = docMgr.getDocument(exprItem.getContainingFile());
          if (doc != null) {
            int line_num = doc.getLineNumber(exprItem.getTextOffset());
            int item_col = exprItem.getTextOffset() - doc.getLineStartOffset(line_num);
            PsiElement here_elt = getNode().getPsi();
            line_num = doc.getLineNumber(here_elt.getTextOffset());
            int node_col = here_elt.getTextOffset() - doc.getLineStartOffset(line_num);
            int padding = item_col - node_col;
            if (padding > 0) { // negative is a syntax error,  but possible
              return Indent.getSpaceIndent(padding);
            }
          }
        }
        return Indent.getContinuationIndent(); // a fallback
      }
    }

    if (afterNode != null && afterNode.getElementType() == PyElementTypes.KEY_VALUE_EXPRESSION) {
      PyKeyValueExpression keyValue = (PyKeyValueExpression) afterNode.getPsi();
      if (keyValue != null && keyValue.getValue() == null) {  // incomplete
        return Indent.getContinuationIndent(true);
      }
    }

    // constructs that imply indent for their children
    if (_node.getElementType().equals(PyElementTypes.PARAMETER_LIST)) {
      return Indent.getContinuationIndent();
    }
    if (ourListElementTypes.contains(_node.getElementType()) || _node.getPsi() instanceof PyStatementPart) {
      return Indent.getNormalIndent();
    }

    return Indent.getNoneIndent();
  }

  @Nullable
  private ASTNode getAfterNode(int newChildIndex) {
    if (newChildIndex == 0) {  // block text contains backslash line wrappings, child block list not built
      return null;
    }
    int prevIndex = newChildIndex - 1;
    while (prevIndex > 0 && _subBlocks.get(prevIndex).getNode().getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
      prevIndex--;
    }
    PyBlock insertAfterBlock = _subBlocks.get(prevIndex);
    return insertAfterBlock.getNode();
  }

  private static ASTNode getLastNonSpaceChild(ASTNode node, boolean acceptError) {
    ASTNode lastChild = node.getLastChildNode();
    while (lastChild != null &&
           (lastChild.getElementType() == TokenType.WHITE_SPACE || (!acceptError && lastChild.getPsi() instanceof PsiErrorElement))) {
      lastChild = lastChild.getTreePrev();
    }
    return lastChild;
  }

  public boolean isIncomplete() {
    // if there's something following us, we're not incomplete
    PsiElement element = _node.getPsi().getNextSibling();
    while (element instanceof PsiWhiteSpace) {
      element = element.getNextSibling();
    }
    if (element != null) {
      return false;
    }

    ASTNode lastChild = getLastNonSpaceChild(_node, false);
    if (lastChild != null && lastChild.getElementType() == PyElementTypes.STATEMENT_LIST) {
      // only multiline statement lists are considered incomplete
      ASTNode statementListPrev = lastChild.getTreePrev();
      if (statementListPrev != null && statementListPrev.getText().indexOf('\n') >= 0) {
        return true;
      }
    }

    if (_node.getPsi() instanceof PyArgumentList) {
      final PyArgumentList argumentList = (PyArgumentList)_node.getPsi();
      return argumentList.getClosingParen() == null;
    }

    return false;
  }

  public boolean isLeaf() {
    return _node.getFirstChildNode() == null;
  }
}
