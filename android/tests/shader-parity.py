#!/usr/bin/env python3
"""Render the app's actual GLSL on Mesa EGL; no Android camera is simulated here."""
import ctypes as C
import json
import os
import pathlib
import sys

os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
os.environ.setdefault('LIBGL_ALWAYS_SOFTWARE', '1')
egl = C.CDLL('libEGL.so.1')
P, I, U, F, B = C.c_void_p, C.c_int, C.c_uint, C.c_float, C.c_ubyte

def api(name, restype, *args):
    fn = getattr(egl, name)
    fn.restype, fn.argtypes = restype, args
    return fn

api('eglGetDisplay', P, P)
api('eglInitialize', U, P, C.POINTER(I), C.POINTER(I))
api('eglChooseConfig', U, P, C.POINTER(I), C.POINTER(P), I, C.POINTER(I))
api('eglBindAPI', U, U)
api('eglCreatePbufferSurface', P, P, P, C.POINTER(I))
api('eglCreateContext', P, P, P, P, C.POINTER(I))
api('eglMakeCurrent', U, P, P, P, P)
api('eglGetProcAddress', P, C.c_char_p)
display = egl.eglGetDisplay(None)
assert egl.eglInitialize(display, None, None), 'EGL initialization failed'
config, count = P(), I()
attrs = (I*13)(0x3033, 1, 0x3040, 4, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3038)
assert egl.eglChooseConfig(display, attrs, C.byref(config), 1, C.byref(count)) and count.value
assert egl.eglBindAPI(0x30A0)
root = pathlib.Path(sys.argv[1])
fixtures = json.loads((root/'cases.json').read_text())
w, h = fixtures['width'], fixtures['height']
surface = egl.eglCreatePbufferSurface(display, config, (I*5)(0x3057,w,0x3056,h,0x3038))
context = egl.eglCreateContext(display, config, None, (I*3)(0x3098,2,0x3038))
assert surface and context and egl.eglMakeCurrent(display, surface, surface, context)

def gl(name, restype, *args):
    address = egl.eglGetProcAddress(name.encode())
    assert address, name
    return C.CFUNCTYPE(restype, *args)(address)

create_shader = gl('glCreateShader', U, U)
shader_source = gl('glShaderSource', None, U, I, C.POINTER(C.c_char_p), P)
compile_shader = gl('glCompileShader', None, U)
get_shader = gl('glGetShaderiv', None, U, U, C.POINTER(I))
shader_log = gl('glGetShaderInfoLog', None, U, I, P, P)

def shader(kind, text):
    result = create_shader(kind)
    code = C.c_char_p(text.encode())
    shader_source(result, 1, C.byref(code), None)
    compile_shader(result)
    status = I()
    get_shader(result, 0x8B81, C.byref(status))
    if not status.value:
        message = C.create_string_buffer(8192)
        shader_log(result, len(message), None, message)
        raise AssertionError(message.value.decode())
    return result

vertex = shader(0x8B31, 'attribute vec2 a;varying vec2 uv;void main(){uv=(a+1.)*.5;gl_Position=vec4(a,0.,1.);}')
# Only substitute the camera sampler: the test texture contains encoded ISP color patches.
fragment_text = (root/'live.frag').read_text().replace('#extension GL_OES_EGL_image_external : require', '').replace('samplerExternalOES', 'sampler2D')
fragment = shader(0x8B30, fragment_text)
program = gl('glCreateProgram', U)()
attach = gl('glAttachShader', None, U, U)
attach(program, vertex); attach(program, fragment)
gl('glLinkProgram', None, U)(program)
status = I()
gl('glGetProgramiv', None, U, U, C.POINTER(I))(program, 0x8B82, C.byref(status))
assert status.value, 'Shader link failed'
gl('glUseProgram', None, U)(program)
texture = U()
gl('glGenTextures', None, I, C.POINTER(U))(1,C.byref(texture))
camera_texture = texture.value
gl('glBindTexture', None, U, U)(0x0DE1,texture)
parameter = gl('glTexParameteri', None, U, U, I)
for name in [0x2801, 0x2800]: parameter(0x0DE1,name,0x2600)
for name in [0x2802, 0x2803]: parameter(0x0DE1,name,0x812F)
input_pixels = C.create_string_buffer((root/'input.rgba').read_bytes())
gl('glTexImage2D', None, U,I,I,I,I,I,U,U,P)(0x0DE1,0,0x1908,w,h,0,0x1908,0x1401,input_pixels)
location = gl('glGetUniformLocation', I,U,C.c_char_p)
one = gl('glUniform1f',None,I,F)
three = gl('glUniform3fv',None,I,I,C.POINTER(F))
for name, value in {'rot':0,'srcAspect':1,'dstAspect':1}.items(): one(location(program,name.encode()),value)
gl('glUniform1i',None,I,I)(location(program,b'cam'),0)
active=gl('glActiveTexture',None,U)
tex_image=gl('glTexImage2D',None,U,I,I,I,I,I,U,U,P)
active(0x84C1);lut_texture=U();gl('glGenTextures',None,I,C.POINTER(U))(1,C.byref(lut_texture));gl('glBindTexture',None,U,U)(0x0DE1,lut_texture)
for name in [0x2801,0x2800]:parameter(0x0DE1,name,0x2600)
for name in [0x2802,0x2803]:parameter(0x0DE1,name,0x812F)
tex_image(0x0DE1,0,0x1908,1,1,0,0x1908,0x1401,C.create_string_buffer(bytes(4)))
gl('glUniform1i',None,I,I)(location(program,b'colorLut'),1);active(0x84C0)
identity = (F*16)(1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1)
gl('glUniformMatrix4fv',None,I,I,B,C.POINTER(F))(location(program,b'st'),1,0,identity)
vertices = (F*8)(-1,-1,1,-1,-1,1,1,1)
a = gl('glGetAttribLocation',I,U,C.c_char_p)(program,b'a')
gl('glEnableVertexAttribArray',None,U)(a)
gl('glVertexAttribPointer',None,U,I,U,B,I,P)(a,2,0x1406,0,0,vertices)
gl('glViewport',None,I,I,I,I)(0,0,w,h)
gl('glDisable',None,U)(0x0BD0)  # Disable dithering for numeric comparisons.
draw = gl('glDrawArrays',None,U,I,I)
read = gl('glReadPixels',None,I,I,I,I,U,U,P)
result = (B*(w*h*4))()
worst, comparisons = 0, 0
for case in fixtures['cases']:
    lut_file=root/f"lut-{case['index']}.rgba"
    if lut_file.exists():
        active(0x84C1);tex_image(0x0DE1,0,0x1908,16,8,0,0x1908,0x1401,C.create_string_buffer(lut_file.read_bytes()));active(0x84C0)
    for name, value in case['uniforms'].items():
        loc = location(program,name.encode())
        if isinstance(value,list): three(loc,1,(F*3)(*value))
        else: one(loc,value)
    draw(5,0,4)
    read(0,0,w,h,0x1908,0x1401,result)
    expected = (root/f"expected-{case['index']}.rgba").read_bytes()
    error = max(abs(result[i]-expected[i]) for i in range(len(expected)) if i%4 != 3)
    assert error <= 2, f"Case {case['index']}: GPU/CPU difference {error}/255"
    comparisons += w*h*3
    worst = max(worst,error)
print(f'PASS: {len(fixtures["cases"])} recipes/settings, {comparisons} RGB channel comparisons; maximum live GLSL vs CPU grade difference {worst}/255 (grain/bloom excluded).')

# Production peaking must affect display only, respect tint/opacity, and leave neutral frames alone.
for name,value in fixtures['cases'][0]['uniforms'].items():
    loc=location(program,name.encode())
    if isinstance(value,list): three(loc,1,(F*3)(*value))
    else: one(loc,value)
one(location(program,b'peaking'),0)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
baseline=bytes(result)
gl('glUniform2f',None,I,F,F)(location(program,b'pixelStep'),1.5/w,1.5/h)
one(location(program,b'peaking'),1)
one(location(program,b'peakOpacity'),0)
three(location(program,b'peakColor'),1,(F*3)(1,0,0))
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
assert bytes(result)==baseline,'Zero opacity must not alter display'
one(location(program,b'peakOpacity'),1)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
red=bytes(result)
changed=[i for i in range(w*h) if red[i*4:i*4+3]!=baseline[i*4:i*4+3]]
assert len(changed)>100,'Detail texture must produce focus hatching'
assert all(red[i*4]>=baseline[i*4] and red[i*4+1]<=baseline[i*4+1] and red[i*4+2]<=baseline[i*4+2] for i in changed),'Red hatching must use chosen tint'
assert any(red[i*4:i*4+3]==baseline[i*4:i*4+3] for i in range(w*h)),'Hatching must leave gaps'
one(location(program,b'peakOpacity'),.5)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
half=bytes(result)
assert all(abs(half[i]-(baseline[i]+red[i])/2)<=1 for i in range(w*h*4) if i%4!=3),'Opacity must blend linearly'
one(location(program,b'peakOpacity'),1)
three(location(program,b'peakColor'),1,(F*3)(0,0,1))
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
blue=bytes(result)
assert all(blue[i*4]<=baseline[i*4] and blue[i*4+2]>=baseline[i*4+2] for i in changed),'Blue selection must change tint'
one(location(program,b'peaking'),0)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
assert bytes(result)==baseline,'Meter/display-off pass must remain exactly unaffected'
flat=C.create_string_buffer(bytes([127,127,127,255])*(w*h))
gl('glTexImage2D',None,U,I,I,I,I,I,U,U,P)(0x0DE1,0,0x1908,w,h,0,0x1908,0x1401,flat)
one(location(program,b'peaking'),0)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result);plain=bytes(result)
one(location(program,b'peaking'),1)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
assert bytes(result)==plain,'Flat areas must not be hatched'
print(f'PASS: production focus hatching on {len(changed)} detail pixels; chosen red/blue colors, gaps, 0/50/100% opacity, flat-field rejection and disabled neutral pass.')

# Exercise the neutral metering pass with every film's non-neutral parameter values present.
gl('glTexImage2D',None,U,I,I,I,I,I,U,U,P)(0x0DE1,0,0x1908,w,h,0,0x1908,0x1401,input_pixels)
original=(root/'input.rgba').read_bytes()
for case in fixtures['cases']:
    for name,value in case['uniforms'].items():
        loc=location(program,name.encode())
        if isinstance(value,list): three(loc,1,(F*3)(*value))
        else: one(loc,value)
    for name in ['strength','exposure','warmth','tint','shadows','highlights','peaking','advanced','noiseLuma','noiseChroma','skinBrightness','skinColor','skinBlush','skinTexture']:
        one(location(program,name.encode()),0)
    draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
    assert max(abs(result[i]-original[i]) for i in range(w*h*4) if i%4!=3)<=1,'Neutral AE histogram must not see film or focus overlay'
print(f'PASS: neutral metering input unchanged across {len(fixtures["cases"])} film/edit configurations, with peaking disabled.')

# Production edge-aware NR sampled on a matching linear-RGB fixture, film disabled.
for name,value in fixtures['cases'][0]['uniforms'].items():
    loc=location(program,name.encode())
    if isinstance(value,list):three(loc,1,(F*3)(*value))
    else:one(loc,value)
one(location(program,b'noiseLuma'),.6);one(location(program,b'noiseChroma'),.8)
gl('glUniform2f',None,I,F,F)(location(program,b'pixelStep'),1.5/w,1.5/h)
one(location(program,b'peaking'),0)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
expected=(root/'expected-noise.rgba').read_bytes()
error=max(abs(result[i]-expected[i]) for i in range(len(expected)) if i%4!=3)
assert error<=2,f'CPU/GPU NR difference {error}/255'
print(f'PASS: production linear luminance/chroma NR GPU/CPU fixture maximum difference {error}/255; full-resolution CPU export uses float samples.')

for mode,texture in enumerate([-.8,.7]):
    for name,value in fixtures['cases'][0]['uniforms'].items():
        loc=location(program,name.encode())
        if isinstance(value,list):three(loc,1,(F*3)(*value))
        else:one(loc,float(value))
    for name in ['peaking','advanced','noiseLuma','noiseChroma']:one(location(program,name.encode()),0)
    for name,value in [('skinTexture',texture),('skinBrightness',.3),('skinColor',.4),('skinBlush',-.2)]:one(location(program,name.encode()),value)
    draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
    expected=(root/f'expected-skin-texture-{mode}.rgba').read_bytes()
    difference=max(abs(result[i]-expected[i]) for i in range(w*h*4) if i%4<3)
    assert difference<=2,f'Skin texture CPU/GPU mismatch {difference}/255'
    print(f'PASS: production skin texture {texture:+.1f} and tone controls vs CPU export: max difference {difference}/255.')

# Fixed press preset: bounded detail, slight contrast/saturation and optional mild NR.
for mode in [0,1]:
    for name,value in fixtures['cases'][0]['uniforms'].items():
        loc=location(program,name.encode())
        if isinstance(value,list):three(loc,1,(F*3)(*value))
        else:one(loc,float(value))
    for name in ['peaking','advanced','skinBrightness','skinColor','skinBlush','skinTexture']:one(location(program,name.encode()),0)
    for name,value in [('detail',.16),('contrast',1.015),('saturation',1.02),('highlights',-.08),('shadows',.04),('noiseLuma',.025 if mode else 0),('noiseChroma',.14 if mode else 0)]:one(location(program,name.encode()),value)
    draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
    expected=(root/f'expected-press-{mode}.rgba').read_bytes()
    difference=max(abs(result[i]-expected[i]) for i in range(w*h*4) if i%4<3)
    assert difference<=3,f'Press preset CPU/GPU mismatch {difference}/255'
    print(f'PASS: press preset detail + NR={bool(mode)} GPU/CPU fixture max difference {difference}/255.')
one(location(program,b'detail'),0)

# Metadata-derived inverse tone/CCM/WB/boost path, using the same production fragment.
preview_fixtures=json.loads((root/'preview-cases.json').read_text())
active(0x84C2);tone_texture=U();gl('glGenTextures',None,I,C.POINTER(U))(1,C.byref(tone_texture));gl('glBindTexture',None,U,U)(0x0DE1,tone_texture)
for name in [0x2801,0x2800]:parameter(0x0DE1,name,0x2600)
for name in [0x2802,0x2803]:parameter(0x0DE1,name,0x812F)
gl('glUniform1i',None,I,I)(location(program,b'toneInverse'),2)
active(0x84C0);gl('glBindTexture',None,U,U)(0x0DE1,camera_texture)
tex_image(0x0DE1,0,0x1908,w,h,0,0x1908,0x1401,C.create_string_buffer((root/'preview-input.rgba').read_bytes()))
for name in ['peaking','advanced','noiseLuma','noiseChroma']:one(location(program,name.encode()),0)
worst=0
for case in preview_fixtures['cases']:
    active(0x84C2);tex_image(0x0DE1,0,0x1908,1024,3,0,0x1908,0x1401,C.create_string_buffer((root/f"preview-tone-{case['model']}.rgba").read_bytes()));active(0x84C0)
    gl('glUniformMatrix3fv',None,I,I,B,C.POINTER(F))(location(program,b'previewMatrix'),1,0,(F*9)(*case['matrix']))
    one(location(program,b'previewScale'),case['scale']);one(location(program,b'previewColor'),1)
    for name,value in case['uniforms'].items():
        loc=location(program,name.encode())
        if isinstance(value,list):three(loc,1,(F*3)(*value))
        else:one(loc,value)
    draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
    expected=(root/f"preview-expected-{case['index']}.rgba").read_bytes()
    error=max(abs(result[i]-expected[i]) for i in range(len(expected)) if i%4!=3)
    assert error<=2,f"Preview case {case['index']}: {error}/255"
    worst=max(worst,error)
# Metering must ignore a live, non-identity display calibration.
one(location(program,b'previewColor'),0)
for name in ['strength','exposure','warmth','tint','shadows','highlights','peaking','advanced','noiseLuma','noiseChroma','skinBrightness','skinColor','skinBlush','skinTexture']:one(location(program,name.encode()),0)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
original=(root/'preview-input.rgba').read_bytes()
assert max(abs(result[i]-original[i]) for i in range(len(original)) if i%4!=3)<=1,'Color calibration contaminated neutral meter'
print(f'PASS:27 metadata preview models/settings,331776 RGB comparisons; GPU/CPU max{worst}/255; neutral meter bypass retained. No physical ISP simulated.')

# Exposure zebra is tied to the signal BEFORE shadow lift / highlight compression.
active(0x84C0);gl('glBindTexture',None,U,U)(0x0DE1,camera_texture)
for name,value in fixtures['cases'][0]['uniforms'].items():
    loc=location(program,name.encode())
    if isinstance(value,list):three(loc,1,(F*3)(*value))
    else:one(loc,float(value))
for name in ['previewColor','peaking','detail','noiseLuma','noiseChroma','advanced','riskSample']:one(location(program,name.encode()),0)
one(location(program,b'zebra'),0)
patches=bytes(sum(([255,255,255,255] if x<w//3 else [0,0,0,255] if x<2*w//3 else [110,130,150,255] for y in range(h) for x in range(w)),[]))
tex_image(0x0DE1,0,0x1908,w,h,0,0x1908,0x1401,C.create_string_buffer(patches))
one(location(program,b'shadows'),.04);one(location(program,b'highlights'),-.08)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result);no_zebra=bytes(result)
one(location(program,b'zebra'),1)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result);hatched=bytes(result)
high=low=unaffected=0
for y in range(h):
    for x in range(w):
        i=(y*w+x)*4
        changed=hatched[i:i+3]!=no_zebra[i:i+3]
        if x<w//3:high+=changed
        elif x<2*w//3:low+=changed
        else:
            assert not changed,'Zebra painted a non-clipped area'
            unaffected+=1
        if changed:assert hatched[i]>=no_zebra[i] and hatched[i+1]<=no_zebra[i+1] and hatched[i+2]<=no_zebra[i+2],'Zebra is not red'
assert high>100 and low>100 and unaffected>100,'Expected highlight and shadow stripes with gaps'
one(location(program,b'riskSample'),1)
draw(5,0,4);read(0,0,w,h,0x1908,0x1401,result)
assert max(abs(result[i]-patches[i]) for i in range(w*h*4) if i%4<3)<=1,'Risk sample was contaminated by preset/zebra'
one(location(program,b'riskSample'),0);one(location(program,b'zebra'),0)
print(f'PASS: red zebra over {high} highlight and {low} shadow pixels, midtones untouched, shadow-lift independent, risk readback unmarked.')
