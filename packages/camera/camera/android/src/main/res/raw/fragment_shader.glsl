#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;

in vec2 v_texCoord;
out vec4 outColor;
uniform samplerExternalOES s_texture;

void grey(inout vec4 color){
    float weightMean = color.r * 0.3 + color.g * 0.59 + color.b * 0.11;
    color.r = color.g = color.b = weightMean;
}

void main(){
    vec4 tmpColor = texture(s_texture, v_texCoord);
    grey(tmpColor);
    outColor = tmpColor;
}