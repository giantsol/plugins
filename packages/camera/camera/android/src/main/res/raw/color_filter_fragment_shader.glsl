#version 300 es
precision mediump float;

in vec2 v_texCoord;
out vec4 outColor;
uniform sampler2D s_texture;
uniform sampler2D textureLUT;

uniform int filterFlag;
uniform float filterIntensity;

void grey(inout vec4 color){
    float weightMean = color.r * 0.3 + color.g * 0.59 + color.b * 0.11;
    color.r = color.g = color.b = weightMean;
}

vec4 lookupTable(vec4 color){
    float blueColor = color.b * 63.0;

    vec2 quad1;
    quad1.y = floor(floor(blueColor) / 8.0);
    quad1.x = floor(blueColor) - (quad1.y * 8.0);
    vec2 quad2;
    quad2.y = floor(ceil(blueColor) / 8.0);
    quad2.x = ceil(blueColor) - (quad2.y * 8.0);

    vec2 texPos1;
    texPos1.x = (quad1.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * color.r);
    texPos1.y = (quad1.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * color.g);
    vec2 texPos2;
    texPos2.x = (quad2.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * color.r);
    texPos2.y = (quad2.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * color.g);
    vec4 newColor1 = texture(textureLUT, texPos1);
    vec4 newColor2 = texture(textureLUT, texPos2);
    vec4 newColor = mix(newColor1, newColor2, fract(blueColor));
    vec4 finalColor = (newColor - color) * filterIntensity + color;
    return vec4(finalColor.rgb, color.w);
}

void main(){
    vec4 tmpColor = texture(s_texture, v_texCoord);
    if (filterFlag == 0) {
//        grey(tmpColor);
        outColor = tmpColor;
    } else {
        outColor = lookupTable(tmpColor);
    }
}