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
        boolean anyPositiveMatches = Iterables.any(positiveCriteria, new Predicate<Criteria>() {
            @Override
            boolean apply(Criteria input) {
                return input.apply(photoData)
            }
        })
        boolean allNegativesMatch = Iterables.all(negativeCriteria, new Predicate<Criteria>() {
            @Override
            boolean apply(Criteria input) {
                return input.apply(photoData)
            }
        })
        return (anyPositiveMatches || positiveCriteria.isEmpty()) && (allNegativesMatch || negativeCriteria.isEmpty())
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
