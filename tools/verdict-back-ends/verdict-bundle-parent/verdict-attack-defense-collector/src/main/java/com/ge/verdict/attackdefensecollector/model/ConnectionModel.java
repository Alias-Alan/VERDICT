package com.ge.verdict.attackdefensecollector.model;

import com.ge.verdict.attackdefensecollector.NameResolver;
import com.ge.verdict.attackdefensecollector.Pair;
import com.ge.verdict.attackdefensecollector.adtree.ADAnd;
import com.ge.verdict.attackdefensecollector.adtree.ADNot;
import com.ge.verdict.attackdefensecollector.adtree.ADOr;
import com.ge.verdict.attackdefensecollector.adtree.ADTree;
import com.ge.verdict.attackdefensecollector.adtree.Attack;
import com.ge.verdict.attackdefensecollector.adtree.Defense;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Stores information about a connection. */
public class ConnectionModel {
    /** The name of the connection. */
    private String name;
    /** The source system. */
    private NameResolver<SystemModel> source;
    /** The destination system. */
    private NameResolver<SystemModel> dest;
    /** The name of the port on the source system. */
    private String sourcePort;
    /** The name of the port on the destination system. */
    private String destPort;

    private Attackable attackable;

    /**
     * Create a new connection.
     *
     * @param name the name of the connection
     * @param source a resolver to the source system
     * @param dest a resolver to the destination system
     * @param sourcePort the name of the source port
     * @param destPort the name of the destination port
     */
    public ConnectionModel(
            String name,
            NameResolver<SystemModel> source,
            NameResolver<SystemModel> dest,
            String sourcePort,
            String destPort) {
        this.name = name;
        this.source = source;
        this.dest = dest;
        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.attackable = new Attackable(this);
    }

    /** @return the name of the connection */
    public String getName() {
        return name;
    }

    /** @return the source system model */
    public SystemModel getSource() {
        return source.get();
    }

    /** @return the destination system model */
    public SystemModel getDestination() {
        return dest.get();
    }

    /** @return the name of the source port */
    public String getSourcePortName() {
        return sourcePort;
    }

    /** @return the name of the destination port */
    public String getDestinationPortName() {
        return destPort;
    }

    public Attackable getAttackable() {
        return attackable;
    }

    /** Map from attacks to defenses that defend against those attacks. */
    private Map<Attack, Defense> attackToDefense;

    /**
     * Build all of the maps used by trace(). This is performed once for significant time complexity
     * improvements.
     */
    public void concretize() {
        attackToDefense = new LinkedHashMap<>();

        Set<Attack> declaredAttacks = new HashSet<>();
        for (Attack attack : attackable.getAttacks()) {
            declaredAttacks.add(attack);
        }

        for (Defense defense : attackable.getDefenses()) {
            // Check that referenced attacks are added to this system
            if (!declaredAttacks.contains(defense.getAttack())) {
                throw new RuntimeException(
                        "Defense in system "
                                + getName()
                                + " refers to non-existant attack "
                                + defense.getAttack().getName());
            }

            attackToDefense.put(defense.getAttack(), defense);
        }
    }

    public boolean isConcretized() {
        return attackToDefense != null;
    }

    private Optional<ADTree> traceInternal(
            CIA cia, Set<Pair<ConnectionModel, CIA>> cyclePrevention) {
        if (!isConcretized()) {
            concretize();
        }

        List<ADTree> children = new ArrayList<>();
        Optional<ADTree> traced =
                getSource().trace(new PortConcern(getSourcePortName(), cia), cyclePrevention);
        if (traced.isPresent()) {
            children.add(traced.get());
        }
        // Attacks which apply directly to this connection
        for (Attack attack : attackable.getAttacks()) {
            // Only allow matching CIA attacks
            if (attack.getCia().equals(cia)) {
                if (attackToDefense.containsKey(attack)) {
                    // There is a defense associated
                    children.add(new ADAnd(new ADNot(attackToDefense.get(attack)), attack));
                } else {
                    // There is no defense, just a raw attack
                    children.add(attack);
                }
            }
        }
        return children.isEmpty() ? Optional.empty() : Optional.of(new ADOr(children));
    }

    /**
     * Trace a port concern through a connection from its destination, constructing an
     * attack-defense tree for all possible attacks on the system.
     *
     * @param connection the connection to trace
     * @param cia the CIA of the port concern
     * @param cyclePrevention a set of previously-traced connections and CIAs, used to prevent
     *     cycles from causing infinite loops
     * @return the optional attack-defense tree constructed from tracing the connection
     */
    protected Optional<ADTree> trace(CIA cia, Set<Pair<ConnectionModel, CIA>> cyclePrevention) {

        Pair<ConnectionModel, CIA> cyc = new Pair<>(this, cia);
        if (!cyclePrevention.contains(cyc)) {
            cyclePrevention.add(cyc);
            return traceInternal(cia, cyclePrevention);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConnectionModel && ((ConnectionModel) other).name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
