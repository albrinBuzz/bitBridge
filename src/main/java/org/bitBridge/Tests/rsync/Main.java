package org.bitBridge.Tests.rsync;

import java.util.*;

public class Main {
    public static ArrayList<Integer>
    rolling_hash(String s, int window_size, int base,
                 int mod)
    {
        int n = s.length();
        ArrayList<Integer> power
                = new ArrayList<Integer>(n + 1);
        ArrayList<Integer> hash_values
                = new ArrayList<Integer>(n - window_size + 1);
        // Precompute the powers of the base modulo the mod
        for (int i = 0; i <= n; i++) {
            power.add(1);
        }
        for (int i = 1; i <= n; i++) {
            power.set(i, (power.get(i - 1) * base) % mod);
        }

        // Compute the hash value of the first window
        int current_hash = 0;
        for (int i = 0; i < window_size; i++) {
            current_hash
                    = (current_hash * base + s.charAt(i)) % mod;
        }
        hash_values.add(current_hash);

        // Compute the hash values of the rest of the
        // substrings
        for (int i = 1; i <= n - window_size; i++) {
            // Remove the contribution of the first
            // character in the window
            current_hash = (current_hash
                    - power.get(window_size - 1)
                    * s.charAt(i - 1))
                    % mod;

            // Shift the window by one character and add the
            // new character to the hash
            current_hash = (current_hash * base
                    + s.charAt(i + window_size - 1))
                    % mod;

            hash_values.add(current_hash);
        }
        return hash_values;
    }

    // Driver code
    public static void main(String[] args)
    {
        // Input string
        String s1 = "abcabcabc";
        String s2 = "abcaxcabc";

        // Window size
        int window_size = 3;

        // Calculate rolling hash values
        ArrayList<Integer> hash_values
                = rolling_hash(s1, window_size, 26, 1000000007);

        // Print the hash values
        for (int val : hash_values) {
            System.out.print(val + " ");
        }
        System.out.println();


        hash_values
                = rolling_hash(s2, window_size, 26, 1000000007);

        // Print the hash values
        for (int val : hash_values) {
            System.out.print(val + " ");
        }
    }
}