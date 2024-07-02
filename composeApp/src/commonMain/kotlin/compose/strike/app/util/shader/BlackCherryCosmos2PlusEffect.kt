package compose.strike.app.util.shader

/**
 *
 * LICENSE "Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License"
 * Author: https://editor.isf.video/u/axiomcrux
 * Credit: https://editor.isf.video/shaders/612cb473f4fe08001a0a6281
 */
const val BLACK_CHERRY_COSMOS_2_PLUS_EFFECT = """
uniform float iTime;
uniform vec3 iResolution;

const int iterations = 5;
const float formuparam2 = 0.89;
const int volsteps = 4;
const float stepsize = 0.390;
const float zoom = 6.900;
const float tile   = 0.850;
const float speed2  = 0.10;
const float brightness = 0.2;
const float darkmatter = 0.400;
const float distfading = 0.560;
const float saturation = 0.800;
const float transverseSpeed  = zoom*2.0;
const float cloud = 0.11;
 
float triangle(float x, float a) {
    float output2 = 2.0*abs(  2.0*  ( (x/a) - floor( (x/a) + 0.5) ) ) - 1.0;
    return output2;
}

float field(in vec3 p) {
	float strength = 7. + .03 * log(1.e-6 + fract(sin(iTime) * 4373.11));
	float accum = 0.;
	float prev = 0.;
	float tw = 0.;
	for (int i = 0; i < 6; ++i) {
		float mag = dot(p, p);
		p = abs(p) / mag + vec3(-.5, -.8 + 0.1*sin(iTime*0.2 + 2.0), -1.1+0.3*cos(iTime*0.15));
		float w = exp(-float(i) / 7.);
		accum += w * exp(-strength * pow(abs(mag - prev), 2.3));
		tw += w;
		prev = mag;
	}
	return max(0., 5. * accum / tw - .7);
}

vec4 main( vec2 fragCoord ) {
    vec2 uv2 = 2. * fragCoord.xy / iResolution.xy - 1.;
	vec2 uvs = uv2 * iResolution.xy / max(iResolution.x, iResolution.y);
    float time = iTime;
	float time2 = time;
    float speed = speed2;
    speed = 0.005 * cos(time2*0.02 + 3.1415926/4.0);
    float formuparam = formuparam2;
	vec2 uv = uvs;
	//mouse rotation
	float a_xz = 0.9;
	float a_yz = -.6;
	float a_xy = 0.9 + time*0.04;
	mat2 rot_xz = mat2(cos(a_xz),sin(a_xz),-sin(a_xz),cos(a_xz));
	mat2 rot_yz = mat2(cos(a_yz),sin(a_yz),-sin(a_yz),cos(a_yz));
	mat2 rot_xy = mat2(cos(a_xy),sin(a_xy),-sin(a_xy),cos(a_xy));
	float v2 =1.0;
	vec3 dir=vec3(uv*zoom,1.);
	vec3 from=vec3(0.0, 0.0,0.0);
    //    from.x -= 5.0*(mouse.x-0.5);
    //    from.y -= 5.0*(mouse.y-0.5);
	vec3 forward = vec3(0.,0.,1.);
	
	from.x += transverseSpeed*(1.0)*cos(0.01*time) + 0.001*time;
		from.y += transverseSpeed*(1.0)*sin(0.01*time) +0.001*time;
	
	from.z += 0.003*time;
	
	dir.xy*=rot_xy;
	forward.xy *= rot_xy;

	dir.xz*=rot_xz;
	forward.xz *= rot_xz;
	
	dir.yz*= rot_yz;
	forward.yz *= rot_yz;
	
	from.xy*=-rot_xy;
	from.xz*=rot_xz;
	from.yz*= rot_yz;
	
	//zoom
	float zooom = (time2-3311.)*speed;
	from += forward* zooom;
	float sampleShift = mod( zooom, stepsize );
	 
	float zoffset = -sampleShift;
	sampleShift /= stepsize; // make from 0 to 1
	
	//volumetric rendering
	float s=0.24;
	float s3 = s + stepsize/2.0;
	vec3 v=vec3(0.);
	float t3 = 0.0;
	
	vec3 backCol2 = vec3(0.);
	for (int r=0; r<volsteps; r++) {
		vec3 p2=from+(s+zoffset)*dir;
		vec3 p3=(from+(s3+zoffset)*dir )* (1.9/zoom);
		
		p2 = abs(vec3(tile)-mod(p2,vec3(tile*2.))); // tiling fold
		p3 = abs(vec3(tile)-mod(p3,vec3(tile*2.))); // tiling fold
		
		t3 = field(p3);
		
		float pa,a=pa=0.;
		for (int i=0; i<iterations; i++) {
			p2=abs(p2)/dot(p2,p2)-formuparam; // the magic formula
			float D = abs(length(p2)-pa); // absolute sum of average change
			
			if (i > 2) {
                a += i > 7 ? min( 12., D) : D;
			}
            pa=length(p2);
		}
		
		a*=a*a; // add contrast
		// brightens stuff up a bit
		float s1 = s+zoffset;
		// need closed form expression for this, now that we shift samples
		float fade = pow(distfading,max(0.,float(r)-sampleShift));
		
		v+=fade;

		// fade out samples as they approach the camera
		if( r == 0 )
			fade *= (1. - (sampleShift));
		// fade in samples as they approach from the distance
		if( r == volsteps-1 )
			fade *= sampleShift;
		v+=vec3(s1,s1*s1,s1*s1*s1*s1)*a*brightness*fade; // coloring based on distance
		
		backCol2 += mix(.4, 1., v2) * vec3(1.8 * t3 * t3 * t3, 1.4 * t3 * t3, t3) * fade;
		
		s+=stepsize;
		s3 += stepsize;
    }
		       
	v=mix(vec3(length(v)),v,saturation); //color adjust

	vec4 forCol2 = vec4(v*.01,1.);
	
	backCol2 *= cloud;
	backCol2.b *= 1.8;
	backCol2.r *= 0.55;
	
	backCol2.b = 0.5*mix(backCol2.g, backCol2.b, 0.8);
	backCol2.g = 0.0;

	backCol2.bg = mix(backCol2.gb, backCol2.bg, 0.5*(cos(time*0.01) + 1.0));
	
	vec4 o =  forCol2 + vec4(backCol2, 1.0);
    if (o.r < .5 && o.g < .5 && o.b < .5) {
        o.a = 0.0;
    }
    return o;
}

"""
