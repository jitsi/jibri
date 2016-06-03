from setuptools import setup
from setuptools import find_packages

setup(
    name="jibri",
    version="0.0.1",
    url='https://github.com/jitsi/jibri',
    license='Apache',
    packages=find_packages(exclude=['tests', 'build', 'dist', 'docs']),
    description="The JItsi BRoadcasting Infrastructure.",
    long_description=open('README.md').read(),
    include_package_data=True,
    zip_safe=False, # We may need the scripts/ at runtime, so don't compress them.
    install_requires=[],
    entry_points={
        'console_scripts': [
            'jibri = jibri.app:main',
            'jibri-selenium = jibri.selenium:main',
            'jibri-custom-stanza-user = jibri.custom_stanza_user:main'
        ],
    },
)
