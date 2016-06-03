from setuptools import setup
from setuptools import find_packages

setup(
    name="jibri",
    use_package_data=True,
    version="0.1",
    packages=['jibri'],
    description="The JItsi BRoadcasting Infrastructure.",
    long_description=open('README.md').read()
)
