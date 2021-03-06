package search;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A RecursiveTask that searches an input string for a list of
 * phrases.
 */
public class SearchForPhrasesTask
       extends RecursiveTask<List<SearchResults>> {
    /**
     * The input string to search.
     */
    private final CharSequence mInputString;

    /**
     * The list of phrases to find.
     */
    private List<String> mPhraseList;

    /**
     * Indicates whether to run the spliterator concurrently.
     */
    private boolean mParallelSearching;

    /**
     * Indicates whether to run the phrases concurrently.
     */
    private boolean mParallelPhrases;

    /**
     * The minimum size of the phrases list to split.
     */
    private final int mMinSplitSize;

    /**
     * Constructor initializes the field.
     */
    public SearchForPhrasesTask(CharSequence inputString,
                                List<String> phraseList,
                                boolean parallelSearching,
                                boolean parallelPhrases) {
        mInputString = inputString;
        mPhraseList = phraseList;
        mParallelSearching = parallelSearching;
        mParallelPhrases = parallelPhrases;
        mMinSplitSize = phraseList.size()/ 2;
    }

    /**
     * This constructor is used internally by the compute() method.
     * It initializes all the fields for the "left hand size" of a
     * split.
     */
    private SearchForPhrasesTask(CharSequence inputString,
                                 List<String> phraseList,
                                 boolean parallelSearching,
                                 boolean parallelPhrases,
                                 int minSplitSize) {
        mInputString = inputString;
        mPhraseList = phraseList;
        mParallelSearching = parallelSearching;
        mParallelPhrases = parallelPhrases;
        mMinSplitSize = minSplitSize;
    }

    /**
     * Perform the computations sequentially at this point.
     */
    private List<SearchResults> computeSequentially() {
        // Create a list to hold the results.
        List<SearchResults> results =
            new ArrayList<>(mPhraseList.size());

        // Get the section title.
        String title = getTitle(mInputString);

        // Skip over the title.
        CharSequence input = mInputString.subSequence(title.length(),
                                                      mInputString.length());

        // Loop through each phrase to find.
        for (String phrase : mPhraseList) {
            // Find all indices where the phrase matches in the input
            // data.
            SearchResults sr =
                new SearchResults
                (Thread.currentThread().getId(),
                 1,
                 phrase,
                 title,
                 // Use a PhraseMatchTask to add the indices of all
                 // places in the inputData where phrase matches.
                 new PhraseMatchTask(input,
                                     phrase,
                                     mParallelSearching).compute());

            // If a phrase was found add it to the list of results.
            if (sr.size() > 0)
                results.add(sr);
        }

        // Return the results.
        return results;
    }

    /**
     * This method searches the @a inputString for all occurrences of
     * the phrases to find.
     */
    @Override
    public List<SearchResults> compute() {
        if (mPhraseList.size() < mMinSplitSize
            || !mParallelPhrases)
            return computeSequentially();
        else 
            // Compute position to split the phrase list and forward
            // to the splitPhraseList() method to perform the split.
            return splitPhraseList(mPhraseList.size() / 2);
    }

    /**
     * Use the fork-join framework to recursively split the input list
     * and return a list of SearchResults that contain all matching
     * phrases in the input list.
     */
    private List<SearchResults> splitPhraseList(int splitPos) {
        // Create and fork a new SearchWithForkJoinTask that
        // concurrently handles the "left hand" part of the input,
        // while "this" handles the "right hand" part of the input.
        ForkJoinTask<List<SearchResults>> leftTask =
            new SearchForPhrasesTask(mInputString,
                                     mPhraseList.subList(0, splitPos),
                                     mParallelSearching,
                                     mParallelPhrases,
                                     mMinSplitSize).fork();

        // Update "this" SearchForPhrasesTask to handle the "right
        // hand" portion of the input.
        mPhraseList = mPhraseList.subList(splitPos, mPhraseList.size());

        // Recursively call compute() to continue the splitting.
        List<SearchResults> rightResult = compute();

        // Wait and join the results from the left task.
        List<SearchResults> leftResult = leftTask.join();

        // sConcatenate the left result with the right result.
        leftResult.addAll(rightResult);

        // Return the result.
        return leftResult;
    }

    /**
     * Return the title portion of the @a inputData.
     */
    private String getTitle(CharSequence input) {
        // Create a Matcher.
        Matcher m = Pattern
            // This regex matches the first line in the input.
            .compile("(?m)^.*$")

            // Create a matcher for this pattern.
            .matcher(input);

        return m.find()
            ? m.group()
            : "";
    }
}

