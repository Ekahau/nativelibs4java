/*
 * ScalaCL - putting Scala on the GPU with JavaCL / OpenCL
 * http://scalacl.googlecode.com/
 *
 * Copyright (c) 2009-2013, Olivier Chafik (http://ochafik.com/)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Olivier Chafik nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY OLIVIER CHAFIK AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package scalacl
package impl

import scala.collection.immutable.Stack
import scala.reflect.NameTransformer

trait KernelSymbolsAnalysis 
extends ConversionNames
with MiscMatchers
{ 
  val global: reflect.api.Universe
  import global._
  import definitions._

  import collection._
  
  sealed trait SymbolKind
  object SymbolKind {
    case object ArrayLike extends SymbolKind
    case object Scalar extends SymbolKind
    case object Other extends SymbolKind
  }
  
  case class KernelSymbols(
    symbolUsages: mutable.HashMap[Symbol, UsageKind] = 
      new mutable.HashMap[Symbol, UsageKind],
    symbolTypes: mutable.HashMap[Symbol, Type] = 
      new mutable.HashMap[Symbol, Type],
    localSymbols: mutable.HashSet[Symbol] = 
      new mutable.HashSet[Symbol]
  ) {
    lazy val symbols: Set[Symbol] = symbolUsages.keySet ++ localSymbols
    lazy val capturedSymbols: Seq[Symbol] = (symbols -- localSymbols).toSeq
    
    def declareSymbolUsage(symbol: Symbol, tpe: Type, usage: UsageKind) {
      if (symbol == NoSymbol) {
        // TODO error("Cannot declare usage of NoSymbol!")
      } else {
        val actualTpe = try { symbol.typeSignature } catch { case _: Throwable => tpe }
        val symbolKind = getKind(symbol, actualTpe)
        if (symbolKind == SymbolKind.Other)
          sys.error("Cannot handle usage of symbol " + symbol + " (with type " + actualTpe + ")")
        /*
        if (tpe.toString.endsWith(".type")) {
          println(s"""
          actualTpe: $actualTpe
          tpe: $tpe
          tpe.normalize: ${tpe.normalize}
          tpe.typeSymbol: ${tpe.typeSymbol}
          symbol: $symbol
          symbol.typeSignature: ${ try { symbol.typeSignature } catch { case ex => ex.toString } }
          """)
        }
        */
        if ((tpe ne null) && actualTpe != NoType) {
          symbolTypes.get(symbol) match {
            case Some(t) =>
              assert(t == actualTpe)
            case None =>
              symbolTypes(symbol) = actualTpe
          }
        }
          
        symbolUsages.get(symbol) match {
          case Some(u) =>
            symbolUsages(symbol) = u.merge(usage)
          case None =>
            symbolUsages(symbol) = usage
        }
      }
    }
  }
  
  private lazy val primTypes = Set(IntTpe, LongTpe, ShortTpe, CharTpe, BooleanTpe, DoubleTpe, FloatTpe, ByteTpe)
  
  private def getKind(symbol: Symbol, tpe: Type): SymbolKind = {
    if (tpe <:< typeOf[CLArray[_]] || tpe <:< typeOf[CLFilteredArray[_]])
      SymbolKind.ArrayLike
    else if (primTypes.find(tpe <:< _) != None)
      SymbolKind.Scalar
    else
      SymbolKind.Other
  }
  
  def getExternalSymbols(tree: Tree, knownSymbols: Set[Symbol] = Set()): KernelSymbols = {
    
    val symbols = new KernelSymbols
    
    new Traverser {
      override def traverse(tree: Tree) = tree match {
        case Ident(n) =>
          if (!knownSymbols.contains(tree.symbol))
            symbols.declareSymbolUsage(tree.symbol, tree.tpe, UsageKind.Input)
        case Apply(Select(target, updateName()), List(index, value)) =>
          if (!knownSymbols.contains(target.symbol))
            symbols.declareSymbolUsage(target.symbol, target.tpe, UsageKind.Output)
          super.traverse(index)
          super.traverse(value)
        case Apply(Select(target, applyName()), List(index)) =>
          if (!knownSymbols.contains(target.symbol))
            symbols.declareSymbolUsage(target.symbol, target.tpe, UsageKind.Input)
          super.traverse(index)
        case ValDef(_, _, _, _) =>
          symbols.localSymbols += tree.symbol
          super.traverse(tree)
        case _ =>
          //val kind = getKind(tree.symbol, tree.tpe)
          //if (kind != SymbolKind.Other) {
          //  symbols.declareSymbolUsage(tree.symbol, tree.tpe, UsageKind.Input)
          //} else 
          {
            //println(s"tree: $tree: ${tree.getClass.getName}")
            super.traverse(tree)
          }
      }
    }.traverse(tree)
    
    symbols
  }
}
