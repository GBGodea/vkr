/**
 * Executable contracts and lightweight proof-obligation helpers.
 *
 * <p>The package follows the standard program-logic reading used by model
 * checking and deductive verification: hypotheses describe the admissible
 * state/input domain, and the conclusion describes the property that must
 * follow. A direct check proves {@code Gamma -> B}; a refutation-style check
 * proves that {@code Gamma & !B} is unsatisfiable.</p>
 */
package ru.vkr.verification;
