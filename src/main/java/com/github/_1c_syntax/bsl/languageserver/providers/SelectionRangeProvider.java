/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.util.Positions;
import org.springframework.stereotype.Component;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SelectionRangeProvider {
  public List<SelectionRange> getSelectionRange(DocumentContext documentContext, SelectionRangeParams params) {

    var positions = params.getPositions();
    var ast = documentContext.getAst();

    // Result must contains all elements from input
    return positions.stream()
      .map(position -> findNodeContainsPosition(ast, position))
      .map(terminalNode -> terminalNode.orElse(null))
      .map(SelectionRangeProvider::toSelectionRange)
      .collect(Collectors.toList());
  }

  @CheckForNull
  private static SelectionRange toSelectionRange(@Nullable ParseTree node) {
    if (node == null) {
      return null;
    }

    var range = Ranges.create(node);

    var selectionRange = new SelectionRange();
    selectionRange.setRange(range);

    nextParentWithDifferentRange(node)
      .map(SelectionRangeProvider::toSelectionRange)
      .ifPresent(selectionRange::setParent);

    return selectionRange;
  }

  private static Optional<ParseTree> nextParentWithDifferentRange(ParseTree ctx) {
    var parent = (BSLParserRuleContext) ctx.getParent();
    if (parent == null) {
      return Optional.empty();
    }

    var currentRange = Ranges.create(ctx);
    var parentRange = Ranges.create(parent);

    if (parentRange.equals(currentRange)) {
      return nextParentWithDifferentRange(parent);
    }

    return Optional.of(parent);
  }

  private static Optional<TerminalNode> findNodeContainsPosition(BSLParserRuleContext tree, Position position) {

    if (tree.getTokens().isEmpty()) {
      return Optional.empty();
    }

    var start = tree.getStart();
    var stop = tree.getStop();

    if (!(positionIsAfterOrOnToken(position, start) && positionIsBeforeOrOnToken(position, stop))) {
      return Optional.empty();
    }

    var children = Trees.getChildren(tree);

    for (Tree child : children) {
      if (child instanceof TerminalNode) {
        var terminalNode = (TerminalNode) child;
        var token = terminalNode.getSymbol();
        if (tokenContainsPosition(token, position)) {
          return Optional.of(terminalNode);
        }
      } else {
        var maybeNode = findNodeContainsPosition((BSLParserRuleContext) child, position);
        if (maybeNode.isPresent()) {
          return maybeNode;
        }
      }
    }

    return Optional.empty();
  }

  private static boolean tokenContainsPosition(Token token, Position position) {
    var tokenRange = Ranges.create(token);
    return Ranges.containsPosition(tokenRange, position);
  }

  private static boolean positionIsBeforeOrOnToken(Position position, Token token) {
    var tokenRange = Ranges.create(token);
    var end = tokenRange.getEnd();
    return Positions.isBefore(position, end) || end.equals(position);
  }

  private static boolean positionIsAfterOrOnToken(Position position, Token token) {
    var tokenRange = Ranges.create(token);
    var start = tokenRange.getStart();
    return Positions.isBefore(start, position) || start.equals(position);
  }


}
