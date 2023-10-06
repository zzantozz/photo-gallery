package rds.photogallery

import com.google.common.base.Function
import com.google.common.base.Predicate
import com.google.common.base.Splitter
import com.google.common.collect.Iterables
import com.google.common.collect.Lists

class TagsFilter implements Predicate<PhotoData> {
    private Collection<Criteria> positiveCriteria
    private Collection<Criteria> negativeCriteria

    TagsFilter(String filterString) {
        Iterable<String> pieces = Splitter.on(",").omitEmptyStrings().split(filterString)
        Iterable<Criteria> criteria = Iterables.transform(pieces, new Function<String, Criteria>() {
            @Override
            Criteria apply(String input) {
                boolean keyed = input.startsWith("-") || input.startsWith("+")
                boolean include = !keyed || input.startsWith("+")
                String tag = keyed ? input.substring(1) : input
                return new Criteria(include, tag)
            }
        })
        positiveCriteria = Lists.newArrayList(Iterables.filter(criteria, new Predicate<Criteria>() {
            @Override
            boolean apply(Criteria input) {
                return input.include
            }
        }))
        negativeCriteria = Lists.newArrayList(Iterables.filter(criteria, new Predicate<Criteria>() {
            @Override
            boolean apply(Criteria input) {
                return !input.include
            }
        }))
    }

    @Override
    boolean apply(final PhotoData photoData) {
        // This is a predicate, meaning it should return true for a match. A match in this case means the candidate
        // should be filtered out, i.e. not be in the list of photos shown.
        //
        // The way tag filtering is supposed to work is:
        //
        // 1. If no filtering is configured, everything should be shown.
        // 2. If there are negative filters, everything except those should be shown.
        // 3. If there are positive filters, only matching photos should be shown.
        // 4. If there are both positive and negative filters, only show photos that match the positives and don't match
        //    the negatives.
        //
        // So, if positive criteria is empty, include it. If positive is specified, include only if it matches. If
        // negative is empty, include it. If negative is specified, include only if it doesn't match. Then combine the
        // positive and negative match to find the answer.
        final boolean positiveMatch;
        if (positiveCriteria.isEmpty()) {
            positiveMatch = true;
        } else {
            positiveMatch = Iterables.any(positiveCriteria, new Predicate<Criteria>() {
                @Override
                boolean apply(Criteria input) {
                    return input.apply(photoData)
                }
            })
        }
        final boolean negativeMatch;
        if (negativeCriteria.isEmpty()) {
            negativeMatch = false;
        } else {
            negativeMatch = Iterables.any(negativeCriteria, new Predicate<Criteria>() {
                @Override
                boolean apply(Criteria input) {
                    return input.apply(photoData)
                }
            })
        }
        // Now, return true if this should be filtered, which would be if there was a negative match or if
        // there was no positive match
        negativeMatch || !positiveMatch
    }

    static class Criteria implements Predicate<PhotoData> {
        private String tag
        private boolean include

        Criteria(boolean include, String tag) {
            this.tag = tag
            this.include = include
        }

        @Override
        boolean apply(PhotoData input) {
            if (include) {
                return input != null && input.getAllTags().contains(tag)
            } else {
                return input == null || !input.getAllTags().contains(tag)
            }
        }
    }
}
