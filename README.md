# Examples.deepartist.org
There are a number of examples released with deepartist.org which attempt to illustrate the concepts of these techniques and the capabilities of this software. These examples are free to use as starting points for any of your art projects. Each example is published to the site as a full example report, with details, code, links, and further images in each.

## Textures & Core Concepts
The basic setup for a deep painting is that you have a canvas, which can be modified at will by the AI process, to match a particular signal. This signal matching network consists of a filter, which modifies the image into more abstract data, and then a loss function which measures how close that data signal is to a reference (i.e. style) signal. These filters and operators can then be scaled and combined to form a limitless variety.

### Pipelines and Layers
A pipeline is a pre-trained deep vision network which is loaded and made available as a series of image processing layers. Each layer builds on the last to extract higher-level details from the source image. This can be illustrated by this example which surveys the sequence of layers in the VGG16 pipeline, using each layer to reconstruct a target signal.

### Operators - Signal Matchers and Enhancers
You can also configure operators, which define how close a signal is to the objective. This objective may be some kind of “similarity measure” to compute how close one image is to another, and many such measures exist; these approach zero. Others simply seek to increase a measure, for example rms “power” as used in DeepDream, possibly with some cutoff before infinity. A survey example of them displays the varied effects:

### Seed Canvas - Noise, Plasma, Image
When a painting process is begun, there must be some data on the canvas to start with. Interestingly, a completely blank canvas produces poor results, due to something called “symmetry breaking”. There are three main types of seed used in deepartist.org: Noise, Plasma, or Image. Noise is simple random white noise, which can be scaled and offset. (For that matter, scaling and offset syntax is available for all image input urls) Plasma is a simple algorithmic texture that resembles a randomly-colored cloud. An actual image can also be given, either as an upload or as a literal url. This example displays a survey of these seed types when used to paint with the same texture parameters:

As you may note, starting from the image results in something that looks like style transfer. This isn’t so much style transfer as warping the content image until it resembles the desired style. Among other differences, it tends to be deterministic - if you run it 3 times, you get nearly the same 3 results.

### Resolution Sequence Texture Generation/Operative Resolutions
Another important factor in the painting process is what resolution we perform it at. As anyone who’s fallen from orbit knows, things look a lot different depending on how far away you are. This variety manifests itself in out painting process by the operative resolution, of what resolution we do a painting operation at. You can see the variety caused by the same painting parameters being performed at small scale, at large scale, and using intermittent stages in this example:

Growing a texture from a smaller image results in a naturally more complex and varied large structure, whereas initial painting using a higher resolution typically produces a more uniform look.

### View Layers
This can be further modified by attaching additional filters to the vision pipeline layers, such as in these examples:

1. Tiled Texture Generation - By wrapping the canvas around itself to enlarge it, we learn to paint regardless of these wrapping boundaries, and will get a tileable image.
1. Kaleidoscope: Rotational Symmetry and Color Permutations - A layer which reflects and rotates space (and color space) can produce an image with a guaranteed symmetry. This can be combined with the tiling layer.

Additionally, the resulting canvas can be post-processed by any other function. One fun example of this is the stereogram generator.

## Style Transfer
Depending on your religion, you may wish to paint a painting to resemble some object or scene. If you insist on your graven images, you can use the content matching operators. These are handled slightly differently than the style operators, but are fully illustrated by the following examples.

### Content Reconstruction Survey
Content images can be reconstructed by signal matching using any vision layer, in the same manner as style matching. Each level higher conveys less local information, such as color, and more higher-level information such as patterns. You can see this in the following survey, which uses each layer in a pipeline to reconstruct target content without any further modifiers.

### Simple Style Transfer
When these content operators are combined with style operators, we can demonstrate classic deep style transfer, such as in this example:

### High-resolution Multi-phase style transfer
For the best results, multiple phases of style transfer processes can be used while progressively enlarging the canvas. By fine-tuning the parameters for each resolution, we can gain better control over the result.

### Animations
There are images, there are videos, and in between there are animated gifs. Animated GIFs are one speciality of DeepArtist.org.

### Surveys
A variety of animations have already been shown, wherein we survey a variety of treatments, and combine them with labels into an animation.

### Sweeps
Another type of animation is to sweep a range of parameters. One example provided is a style transfer sweep from one style to another:

### Determinism and Jitter
Why start with white noise when we do a style transfer? Wouldn’t it be faster to start with the content image itself and change it to match the desired style? One reason is that this heavily biases the result in a way you will have trouble controlling, but another is that the result is deterministic. If we start with noise, there is an inherent randomness to our resulting image that makes it unique. If we use this randomness to produce an animation, we get a unique jittery effect:

This can also be combined with kaleidoscopic textures:

