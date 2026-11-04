# Planet sky art

The sky artwork takes technical inspiration from Farworlds (comedy01, MIT): its `src/shaders/space.fsh` uses
domain-warped noise, dark dust and a star magnitude distribution; `planet.fsh` evaluates detail on a sphere,
with terrain relief and an atmospheric limb. Nerospace implements these ideas independently in its existing
Python texture generator and mesh renderer. No Farworlds source, shaders or assets are bundled, and no new mod
dependency is required.

Nerospace's direction is colourful cosmic skies: rose and indigo galaxies, copper and violet Martian filaments,
cyan and lilac glacial clouds, emerald wisps and Nyxara's magenta and turquoise rift. Dark branching dust lanes
give the luminous clouds depth. Its existing planet clocks, eclipse, atmospheric events and
moving aurora curtains supply motion. Dust structures stay fixed against the stars.

`tools/gen_sky_textures.py` samples seeded volume noise on spheres for bodies and around a cylinder for galaxies.
This compresses detail at the limb and closes the galaxy seam. Detail is baked offline, keeping the runtime's
cached meshes and two star draws. Point sprites have sampled Gaussian cores so even-sized sprites cannot lose
their centre between pixels. Star directions are uniform over the sphere, with mostly small points and rare
larger stars. Full custom skies currently use the NeoForge 26.3 renderer; other cells retain their existing
vanilla sky shapes and planet colours.

The sky uses the camera's original projection before Minecraft adds walking bob and hurt effects, then restores
the world projection in a `finally` block. Walking still affects the player's normal world view. The solid lower
sky reaches the nadir, so it has no cutoff below the horizon. Each draw receives its own pooled matrix and tint:
Minecraft retains references when comparing its last uniform, so mutating a single shared pair would incorrectly
reuse the background transform and colour for stars and bodies.

The sky pass has no terrain-depth attachment. Ground worlds also fade the lowest twelve degrees of sky into the
same scene fog used by terrain.
The orbital station omits this ground fog veil to keep its homeworld visible below the horizon.

Horizon ramps must use linear, clamped sampling. The lower hemisphere holds texture V at 1: with a repeating
sampler, rasterization rounding can sample either the opaque bottom row or the transparent top row. This caused
the speckled band below the horizon on every planet. Both ramp textures declare `clamp` and `blur` in their
`.png.mcmeta`, and the renderer explicitly binds a clamp sampler for the gradient, haze and ground fog passes.
Aurora curtains and cloud decks retain their intentional repeating samplers. `SkyAssetsTest` pins the ramp
sampling metadata and the transparent/opaque endpoints.

Regenerate deliberately with `python tools/gen_sky_textures.py --force --preview build/sky-art`.
Check reproducibility with `python tools/gen_sky_textures.py --check` and run generator regression tests with
`python -m unittest discover -s tools -p test_sky_textures.py` (Pillow and NumPy required).
The normal Java sky tests check profile codecs, referenced assets, day/night visibility and weather occlusion.
Use the in-game sky gallery to inspect clear nights, Earthrise, the eclipse and weather; a texture preview alone
does not verify the rendering pipeline.
