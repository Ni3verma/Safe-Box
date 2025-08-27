package com.andryoga.composeapp.security.interfaces

interface HashingUtils {
    /*
     * @author Nitin
     * Computes hash of passed input
     * @param password : simple string that needs to be hashed
     * @return {base64 encoded hash(password+salt)}+someSeparator+{base64 encoded salt}*/
    fun hash(password: String): String

    /*
     * @author Nitin
     * compares if text matches with passed in hash or not
     * @param toCompareText : it is the string that needs to be compared
     * @param toCompareWithHash : it the hash that will be compared with.
     * It must be of this format : {base64 encoded hash(password+salt)}+someSeparator+{base64 encoded salt}
     * @return true if text and its hash matches, false otherwise*/
    fun compareHash(toCompareText: String, toCompareWithHash: String): Boolean
}
