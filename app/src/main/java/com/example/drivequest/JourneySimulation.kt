package com.example.drivequest

// Fictional route playback and speed integration were removed. Distances come only from SDK callbacks.
fun journeyTime(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
