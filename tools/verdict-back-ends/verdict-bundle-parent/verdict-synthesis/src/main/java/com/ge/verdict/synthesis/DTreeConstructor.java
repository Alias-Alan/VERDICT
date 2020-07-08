package com.ge.verdict.synthesis;

import com.ge.verdict.attackdefensecollector.AttackDefenseCollector;
import com.ge.verdict.attackdefensecollector.adtree.ADAnd;
import com.ge.verdict.attackdefensecollector.adtree.ADNot;
import com.ge.verdict.attackdefensecollector.adtree.ADOr;
import com.ge.verdict.attackdefensecollector.adtree.ADTree;
import com.ge.verdict.attackdefensecollector.adtree.Attack;
import com.ge.verdict.attackdefensecollector.adtree.Defense;
import com.ge.verdict.synthesis.dtree.ALeaf;
import com.ge.verdict.synthesis.dtree.DAnd;
import com.ge.verdict.synthesis.dtree.DLeaf;
import com.ge.verdict.synthesis.dtree.DNot;
import com.ge.verdict.synthesis.dtree.DOr;
import com.ge.verdict.synthesis.dtree.DTree;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DTreeConstructor {
    /**
     * Construct a defense tree for multiple cyber requirements.
     *
     * @param results
     * @param costModel
     * @param usePartialSolution
     * @param factory
     * @return
     */
    public static DTree construct(
            List<AttackDefenseCollector.Result> results,
            CostModel costModel,
            boolean usePartialSolution,
            DLeaf.Factory factory) {
        return new DAnd(
                results.stream()
                        .map(
                                result ->
                                        construct(
                                                result.adtree,
                                                costModel,
                                                result.cyberReq.getSeverityDal(),
                                                usePartialSolution,
                                                factory))
                        .collect(Collectors.toList()));
    }

    /**
     * Construct a defense tree for one cyber requirement.
     *
     * @param adtree
     * @param costModel
     * @param targetDal
     * @param usePartialSolution
     * @param factory
     * @return
     */
    public static DTree construct(
            ADTree adtree,
            CostModel costModel,
            int targetDal,
            boolean usePartialSolution,
            DLeaf.Factory factory) {
        return (new DTreeConstructor(costModel, targetDal, usePartialSolution, factory))
                .perform(adtree);
    }

    private final CostModel costModel;
    private final DLeaf.Factory factory;
    private final int targetDal;
    private final boolean usePartialSolution;

    private final Set<Defense> defenses;
    private final Map<Attack, Set<ALeaf>> attackALeafMap;

    private DTreeConstructor(
            CostModel costModel, int dal, boolean usePartialSolution, DLeaf.Factory factory) {
        this.costModel = costModel;
        this.factory = factory;
        this.targetDal = dal;
        this.usePartialSolution = usePartialSolution;

        defenses = new LinkedHashSet<>();
        attackALeafMap = new LinkedHashMap<>();
    }

    private DTree perform(ADTree adtree) {
        Optional<DTree> resultOpt = constructInternal(adtree);

        if (!resultOpt.isPresent()) {
            return new DOr(Collections.emptyList());
        } else {
            DTree result = resultOpt.get();

            for (Defense defense : defenses) {
                if (!attackALeafMap.containsKey(defense.getAttack())) {
                    throw new RuntimeException(
                            "defense references undefined attack: "
                                    + defense.getAttack().getName());
                }
                // set each defended attack leaf to mitigated so that
                // it isn't included in the final tree
                for (ALeaf aleaf : attackALeafMap.get(defense.getAttack())) {
                    aleaf.setMitigated();
                }
            }

            Set<ALeaf> unmitigated = new LinkedHashSet<>();
            for (Set<ALeaf> set : attackALeafMap.values()) {
                for (ALeaf aleaf : set) {
                    if (!aleaf.isMitigated()) {
                        unmitigated.add(aleaf);
                    }
                }
            }

            for (ALeaf aleaf : unmitigated) {
                System.out.println("Warning: Unmitigated attack: " + aleaf.getAttack().toString());
            }

            Optional<DTree> prepared = result.prepare();
            return prepared.orElse(new DOr(Collections.emptyList()));
        }
    }

    private Optional<DTree> constructInternal(ADTree adtree) {
        if (adtree instanceof Attack) {
            Attack attack = (Attack) adtree;
            ALeaf aleaf = new ALeaf(attack);
            // keep track of all attack leaves
            if (!attackALeafMap.containsKey(attack)) {
                attackALeafMap.put(attack, new LinkedHashSet<>());
            }
            attackALeafMap.get(attack).add(aleaf);
            return Optional.of(aleaf);
        } else if (adtree instanceof Defense) {
            Defense defense = (Defense) adtree;
            defenses.add(defense);
            return Optional.of(new DNot(constructDefenseTree(defense)));
        } else if (adtree instanceof ADAnd) {
            ADAnd adand = (ADAnd) adtree;
            // Transpose and/or
            return Optional.of(
                    new DOr(
                            adand.children().stream()
                                    .map(this::constructInternal)
                                    .flatMap(
                                            elem ->
                                                    elem.isPresent()
                                                            ? Stream.of(elem.get())
                                                            : Stream.empty())
                                    .collect(Collectors.toList())));
        } else if (adtree instanceof ADOr) {
            ADOr ador = (ADOr) adtree;
            // Transpose and/or
            return Optional.of(
                    new DAnd(
                            ador.children().stream()
                                    .map(this::constructInternal)
                                    .flatMap(
                                            elem ->
                                                    elem.isPresent()
                                                            ? Stream.of(elem.get())
                                                            : Stream.empty())
                                    .collect(Collectors.toList())));
        } else if (adtree instanceof ADNot) {
            ADNot adnot = (ADNot) adtree;
            return constructInternal(adnot.child()).map(DNot::new);
        } else {
            throw new RuntimeException(
                    "got invalid adtree type: " + adtree.getClass().getCanonicalName());
        }
    }

    private DTree constructDefenseTree(Defense defense) {
        return new DOr(
                defense.getDefenseDnf().stream()
                        .map(
                                term ->
                                        new DAnd(
                                                term.stream()
                                                        .map(leaf -> constructDLeaf(defense, leaf))
                                                        .collect(Collectors.toList())))
                        .collect(Collectors.toList()));
    }

    private DLeaf constructDLeaf(Defense defense, Defense.DefenseLeaf leaf) {
        String system = defense.getAttack().getAttackable().getParentName();
        String attack = defense.getAttack().getName();
        String defenseProp = leaf.left;
        int implDal = leaf.right.isPresent() ? leaf.right.get().right : 0;

        return new DLeaf(
                system,
                defenseProp,
                attack,
                implDal,
                targetDal,
                costModel,
                factory,
                usePartialSolution);
    }
}
