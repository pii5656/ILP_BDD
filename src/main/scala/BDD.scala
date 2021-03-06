import net.sf.javabdd.BDD
import net.sf.javabdd.BDDFactory

import collection.JavaConversions._

trait BDDUtil{
  def seqAnd(bdds: Seq[BDD]): BDD = {
    def seqAndLoop(bdds: Seq[BDD], result: BDD): BDD = {
      if(bdds.length == 0)
        result
      else{
        val bdd = bdds.head
        val new_result = bdd.and(result)
        seqAndLoop(bdds.tail, new_result)
      }
    }
    seqAndLoop(bdds.tail, bdds.head)
  }
  def seqOr(bdds: Seq[BDD]): BDD = {
    def seqOrLoop(bdds: Seq[BDD], result: BDD): BDD = {
      if(bdds.length == 0)
        result
      else{
        val bdd = bdds.head
        val new_result = bdd.or(result)
        seqOrLoop(bdds.tail, new_result)
      }
    }
    seqOrLoop(bdds.tail, bdds.head)
  }
}

trait BDDAlgo{
  def inf = 99999999


  def minimumWeight(bdd: BDD, weights: Array[Int]): Array[Int] = {
    // BDD -> (m, t)
    var mts: Map[BDD,(Int,Boolean)] = Map.empty

    def minimumWeightLoop(bdd: BDD): Int = {
      if(bdd.isZero){
        inf
      }else if(bdd.isOne){
        0
      }else{
        val before_mt = mts.get(bdd)
        before_mt match {
          // already visited
          case Some((m,t)) => m
          // first time
          case None =>
            val lw = minimumWeightLoop(bdd.low)
            val hw = minimumWeightLoop(bdd.high) + weights(bdd.`var`)
            if(lw < hw){
              // update mts map
              mts += (bdd -> (lw,false))
              lw
            }else{
              // update mts map
              mts += (bdd -> (hw,true))
              hw
          }
        }
      }
    }

    println("calculate ms ts...")
    // calculate ms ts
    minimumWeightLoop(bdd)

    // create x's
    // init xs = Array(0,0,...,0)
    var xs: Array[Int] = Array.fill(weights.size)(0)
    var tmp_bdd = bdd

    // from root to bottom
    while(tmp_bdd.nodeCount > 1){
      println(tmp_bdd.nodeCount)
      val mt = mts.get(tmp_bdd)
      println(mt)
      mt match {
        case Some((m,t)) =>
          if(t){
            xs(tmp_bdd.`var`) = 1
            tmp_bdd = tmp_bdd.high
          }else{
            xs(tmp_bdd.`var`) = 0
            tmp_bdd = tmp_bdd.low
          }
        case _ => throw new Exception("Internal Error: Invalid mts")
      }
    }
    return xs
  }

  def minimumWeightTai(b: BDDFactory, bdd: BDD, weights: Array[Int]): BDD = {
    // BDD -> (m, t)
    var mts: Map[BDD,(Int,Int)] = Map.empty

    def minimumWeightLoop(bdd: BDD): Int = {
      if(bdd.isZero){
        inf
      }else if(bdd.isOne){
        0
      }else{
        val before_mt = mts.get(bdd)
        before_mt match {
          // already visited
          case Some((m,t)) => m
          // first time
          case None =>
            val lw = minimumWeightLoop(bdd.low)
            val hw = minimumWeightLoop(bdd.high) + weights(bdd.`var`)
            if(lw < hw){
              // update mts map
              mts += (bdd -> (lw,0))
              lw
            }else if(lw == hw){
              mts += (bdd -> (lw,2))
              lw
            }else {
              // update mts map
              mts += (bdd -> (hw,1))
              hw
          }
        }
      }
    }

    println("calculate ms ts...")
    // calculate ms ts
    minimumWeightLoop(bdd)

    // create x's
    // init xs = Array(0,0,...,0)
    //    var xs: Array[Int] = Array.fill(weights.size)(0)
    // ルートから順にたどり, スコアが最良のときの仮説を表すBDDを構築する.
    // パスに現れない変数は仮説に含まれないとする.
    def rootToBottomLoop(bdd: BDD): BDD ={
      if(bdd.nodeCount == 0){
        b.one
        // high == Trueのときノードを残す
//        if(bdd.high.isOne)
//          bdd
        // high == Falseのときノードを残さない
//        else
//          b.one
      } else {
        val mt = mts.get(bdd)
        mt match {
          // Trueになるべきという情報だけを保持する.
          case Some((m,t)) =>
            if(t == 1){
              (b.ithVar(bdd.`var`)).and(rootToBottomLoop(bdd.high))
            }else if(t == 0){
              (b.ithVar(bdd.`var`).not).and(rootToBottomLoop(bdd.low))
              //rootToBottomLoop(bdd.low)
            } else{ // t == 2
              (b.ithVar(bdd.`var`)).and(rootToBottomLoop(bdd.high))
                .or((b.ithVar(bdd.`var`).not).and(rootToBottomLoop(bdd.low)))
            }
          case _ => throw new Exception("Internal Error: Invalid mts")
        }
      }
    }
    rootToBottomLoop(bdd)
  }
}

object BDDMain extends BDDUtil with BDDAlgo{
  def dataToBDD(data: RelationalData, clauses: List[DefiniteClause], b: BDDFactory): BDD = {
    val attrs_data = data.attrs
    val dep_clauses = PredicateLogic.getDependentClauses(attrs_data,clauses)

    val indexes = dep_clauses.map{c => clauses.indexOf(c)}
    val nodes = indexes.map{i => b.ithVar(i)}
    val bdd = seqOr(nodes.toSeq)
    bdd
  }

  def buildBDDFromRelationalData(b: BDDFactory, datas: List[RelationalData], possible_values: Map[String, Set[Const]], clauses: List[DefiniteClause], positive_symbol: String, body_length: Int) = {

    b.setVarNum(clauses.size)
    var i = 0
    val label_bdds = datas.map{data =>
      print("data")
      println(i)
      i += 1
      (data.label, BDDMain.dataToBDD(data,clauses,b))}
    val positive_bdds = label_bdds.filter{lb => lb._1 == positive_symbol}.map{lb => lb._2}
    val negative_bdds = label_bdds.filter{lb => lb._1 != positive_symbol}.map{lb => lb._2.not}
    val positive_bdd = seqAnd(positive_bdds)
    val negative_bdd = seqAnd(negative_bdds)
    positive_bdd.and(negative_bdd)
  }

  def numToBDD(b: BDDFactory, num_literal: PredicateSymbol, clauses: List[DefiniteClause], num_bdd_map: Map[PredicateSymbol, BDD]): BDD = {

    val dep_clauses = NumPredicateLogic.getCountedDefiniteClauses(num_literal, clauses)

    val or_operands: List[BDD] = dep_clauses.map{ dep_clause =>
      // clause to BDD
      dep_clause match {
        case DefiniteClause(head, body @_*) =>
          if(body.size == 0){
            b.ithVar(clauses.indexOf(dep_clause))
          } else {
            val i = b.ithVar(clauses.indexOf(dep_clause))
            val fs = body.map{ body_literal_num =>
              body_literal_num match{
                case PredicateSymbol(name, Const(num)) =>  num_bdd_map.getOrElse(body_literal_num, b.zero)
                  // e(s(x)) <- e(x)のとき, pre: 1
                  // F_{e(s^2(0))}を考えるとき head_num: 2
                  // e(s(x))に代入x<-s(x)したF_{e(s(x))}をもってくる
                case PredicateSymbol(name, NumVar(var_name, pre)) =>
                  num_literal match{
                    case PredicateSymbol(num_literal_name, Const(num_literal_num)) =>
                      // headはdep_clause のhead <- body_1, ..., body_n
                      // n > 1なので, headはPredicateSymbol(name, NumVar)のみ
                      head match{
                        case PredicateSymbol(head_name, NumVar(head_var_name, head_num)) =>
                          // 代入する値は
                          // F_{e(s^2(0))} = ... \/ (I_{e(s(x)) <- e(x)} /\ F_{e{s(0)}}) \/ ...
                          // s(0) = s^2(0) - e(s(0))
                          val diff_const = num_literal_num.toInt - head_num.toInt
                          val substituted_const = Const((pre.toInt + diff_const).toString)
                          num_bdd_map.getOrElse(PredicateSymbol(name, substituted_const), b.zero)
                      }
                  }
              }
            }
            seqAnd(List(List(i), fs).flatten)
          }
      }
    }
    val result = seqOr(or_operands)
    result
  }

  def numsToBDD(b:  BDDFactory, num_literals: List[PredicateSymbol], positive_function: Int => Boolean, clauses: List[DefiniteClause]): BDD = {
    val max_num = NumPredicateLogic.literalToInt(num_literals.last)

    var num_bdd_map: Map[PredicateSymbol, BDD] = Map()
    val num_bdds = num_literals.map{ num_literal =>
      val bdd = numToBDD(b, num_literal, clauses, num_bdd_map)
      num_bdd_map += (num_literal -> bdd)
      (num_literal, bdd)
    }
    val positive_bdds = num_literals.filter{x => x match{
      case PredicateSymbol(name, Const(num)) => positive_function(num.toInt)}}
      .map{p_lit => num_bdd_map.getOrElse(p_lit, b.zero)}
    val negative_bdds = num_literals.filter{x => x match{
      case PredicateSymbol(name, Const(num)) => !positive_function(num.toInt)}}
      .map{n_lit => num_bdd_map.getOrElse(n_lit, b.zero)}
    seqAnd(positive_bdds).and(seqAnd(negative_bdds.map{_.not}))
  }

}
