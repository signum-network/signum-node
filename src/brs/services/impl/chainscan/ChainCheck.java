package brs.services.impl.chainscan;

import java.util.List;

/** One rule, or a group of closely related rules, that the chain scan checks for every block. */
public interface ChainCheck {

    /** The check group this check belongs to, as selected with {@code --checks}: {@code tx} or {@code block}. */
    String group();

    /**
     * Checks one block.
     *
     * @return the violations found, empty if none
     */
    List<Finding> check(BlockContext context);
}
