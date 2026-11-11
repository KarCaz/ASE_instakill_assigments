package com.instakill.media.processing;

/**
 * Transforms an uploaded image before it is stored.
 *
 * <p>The contract is deliberately plain bytes-in / bytes-out with no web or
 * storage types, so this module can later run on its own (e.g. as a queue
 * consumer or background worker) without changes to its callers' signatures.
 */
public interface ImageProcessor {

    /** Whether files with this extension (lower case, no dot) can be processed. */
    boolean supports(String extension);

    /**
     * @param input     the original image bytes
     * @param extension lower-case extension, decides the output format
     * @return the processed image encoded in the same format
     * @throws ImageProcessingException if the bytes are not a readable image
     */
    byte[] process(byte[] input, String extension);
}
