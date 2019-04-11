#!/usr/bin/python

# -*- coding: utf-8 -*-

"""
PyCOMPSs Testbench
========================
"""

# Imports
import unittest

from modules.testCollectionInFunctions import testCollectionInFunctions


def main():
    suite = unittest.TestLoader().loadTestsFromTestCase(testCollectionInFunctions)
    unittest.TextTestRunner(verbosity=2).run(suite)


if __name__ == "__main__":
    main()