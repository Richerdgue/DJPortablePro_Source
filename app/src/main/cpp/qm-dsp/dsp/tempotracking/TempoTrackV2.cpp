/* -*- c-basic-offset: 4 indent-tabs-mode: nil -*-  vi:set ts=8 sts=4 sw=4: */

/*
    QM DSP Library

    Centre for Digital Music, Queen Mary, University of London.
    This file copyright 2008-2009 Matthew Davies and QMUL.

    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License as
    published by the Free Software Foundation; either version 2 of the
    License, or (at your option) any later version.  See the file
    COPYING included with this distribution for more information.
*/

#include "TempoTrackV2.h"
#include "maths/MathUtilities.h"

#include <cmath>
#include <cstdlib>
#include <iostream>

using std::vector;

#define   EPS 0.0000008

TempoTrackV2::TempoTrackV2(float rate, int increment) :
    m_rate(rate), m_increment(increment) {
}

TempoTrackV2::~TempoTrackV2() { }

void
TempoTrackV2::filter_df(d_vec_t &df)
{
    int df_len = int(df.size());

    d_vec_t a(3);
    d_vec_t b(3);
    d_vec_t lp_df(df_len);

    a[0] = 1.0000;
    a[1] = -0.3695;
    a[2] = 0.1958;
    b[0] = 0.2066;
    b[1] = 0.4131;
    b[2] = 0.2066;

    double inp1 = 0.;
    double inp2 = 0.;
    double out1 = 0.;
    double out2 = 0.;

    for (int i = 0; i < df_len; i++) {
        lp_df[i] =  b[0]*df[i] + b[1]*inp1 + b[2]*inp2 - a[1]*out1 - a[2]*out2;
        inp2 = inp1;
        inp1 = df[i];
        out2 = out1;
        out1 = lp_df[i];
    }

    for (int i = 0; i < df_len; i++) {
        df[i] = lp_df[df_len - i - 1];
    }

    for (int i = 0; i < df_len; i++) {
        lp_df[i] = 0.;
    }

    inp1 = 0.; inp2 = 0.;
    out1 = 0.; out2 = 0.;

    for (int i = 0; i < df_len; i++) {
        lp_df[i] =  b[0]*df[i] + b[1]*inp1 + b[2]*inp2 - a[1]*out1 - a[2]*out2;
        inp2 = inp1;
        inp1 = df[i];
        out2 = out1;
        out1 = lp_df[i];
    }

    for (int i = 0; i < df_len; i++) {
        df[i] = lp_df[df_len - i - 1];
    }
}

void
TempoTrackV2::calculateBeatPeriod(const vector<double> &df,
                                  vector<double> &beat_period,
                                  vector<double> &tempi,
                                  double inputtempo, bool constraintempo)
{
    int wv_len = 128;
    double rayparam = (60.0 * m_rate / m_increment) / inputtempo;

    d_vec_t wv(wv_len);

    if (constraintempo) {
        for (int i = 0; i < wv_len; i++) {
            wv[i] = exp( (-1.*pow((double(i)-rayparam),2.)) / (2.*pow(rayparam/4.,2.)) );
        }
    } else {
        for (int i = 0; i < wv_len; i++) {
            wv[i] = (double(i) / pow(rayparam,2.)) * exp((-1.*pow(-double(i),2.)) / (2.*pow(rayparam,2.)));
        }
    }

    int winlen = 512;
    int step = 128;

    d_mat_t rcfmat;
    int col_counter = -1;
    int df_len = int(df.size());

    for (int i = 0; i+winlen < df_len; i+=step) {
        d_vec_t dfframe(winlen);
        for (int k=0; k < winlen; k++) {
            dfframe[k] = df[i+k];
        }
        d_vec_t rcf(wv_len);
        get_rcf(dfframe,wv,rcf);

        rcfmat.push_back( d_vec_t() );
        col_counter++;
        for (int j = 0; j < wv_len; j++) {
            rcfmat[col_counter].push_back( rcf[j] );
        }
    }

    viterbi_decode(rcfmat,wv,beat_period,tempi);
}

void
TempoTrackV2::get_rcf(const d_vec_t &dfframe_in, const d_vec_t &wv, d_vec_t &rcf)
{
    d_vec_t dfframe(dfframe_in);

    MathUtilities::adaptiveThreshold(dfframe);

    int dfframe_len = int(dfframe.size());
    int rcf_len = int(rcf.size());

    d_vec_t acf(dfframe_len);

    for (int lag = 0; lag < dfframe_len; lag++) {
        double sum = 0.;
        double tmp = 0.;

        for (int n = 0; n < (dfframe_len - lag); n++) {
            tmp = dfframe[n] * dfframe[n + lag];
            sum += tmp;
        }
        acf[lag] = double(sum/ (dfframe_len - lag));
    }

    int numelem = 4;

    for (int i = 2; i < rcf_len; i++) {
        for (int a = 1; a <= numelem; a++) {
            for (int b = 1-a; b <= a-1; b++) {
                rcf[i-1] += ( acf[(a*i+b)-1]*wv[i-1] ) / (2.*a-1.);
            }
        }
    }

    MathUtilities::adaptiveThreshold(rcf);

    double rcfsum =0.;
    for (int i = 0; i < rcf_len; i++) {
        rcf[i] += EPS ;
        rcfsum += rcf[i];
    }

    for (int i = 0; i < rcf_len; i++) {
        rcf[i] /= (rcfsum + EPS);
    }
}

void
TempoTrackV2::viterbi_decode(const d_mat_t &rcfmat, const d_vec_t &wv, d_vec_t &beat_period, d_vec_t &tempi)
{
    int wv_len = int(wv.size());

    d_mat_t tmat;
    for (int i = 0; i < wv_len; i++) {
        tmat.push_back ( d_vec_t() );
        for (int j = 0; j < wv_len; j++) {
            tmat[i].push_back(0.);
        }
    }

    double sigma = 8.;
    for (int i = 20; i  < wv_len - 20; i++) {
        for (int j = 20; j < wv_len - 20; j++) {
            double mu = double(i);
            tmat[i][j] = exp( (-1.*pow((j-mu),2.)) / (2.*pow(sigma,2.)) );
        }
    }

    d_mat_t delta;
    i_mat_t psi;
    for (int i = 0; i < int(rcfmat.size()); i++) {
        delta.push_back(d_vec_t());
        psi.push_back(i_vec_t());
        for (int j = 0; j < int(rcfmat[i].size()); j++) {
            delta[i].push_back(0.);
            psi[i].push_back(0);
        }
    }

    int T = int(delta.size());

    if (T < 2) return;

    int Q = int(delta[0].size());

    for (int j = 0; j < Q; j++) {
        delta[0][j] = wv[j] * rcfmat[0][j];
        psi[0][j] = 0;
    }

    double deltasum = 0.;
    for (int i = 0; i < Q; i++) {
        deltasum += delta[0][i];
    }
    for (int i = 0; i < Q; i++) {
        delta[0][i] /= (deltasum + EPS);
    }

    for (int t=1; t < T; t++)
    {
        d_vec_t tmp_vec(Q);

        for (int j = 0; j < Q; j++) {
            for (int i = 0; i < Q; i++) {
                tmp_vec[i] = delta[t-1][i] * tmat[j][i];
            }

            delta[t][j] = get_max_val(tmp_vec);
            psi[t][j] = get_max_ind(tmp_vec);
            delta[t][j] *= rcfmat[t][j];
        }

        double deltasum = 0.;
        for (int i = 0; i < Q; i++) {
            deltasum += delta[t][i];
        }
        for (int i = 0; i < Q; i++) {
            delta[t][i] /= (deltasum + EPS);
        }
    }

    i_vec_t bestpath(T);
    d_vec_t tmp_vec(Q);
    for (int i = 0; i < Q; i++) {
        tmp_vec[i] = delta[T-1][i];
    }

    bestpath[T-1] = get_max_ind(tmp_vec);

    for (int t=T-2; t>0 ;t--) {
        bestpath[t] = psi[t+1][bestpath[t+1]];
    }

    bestpath[0] = psi[1][bestpath[1]];

    int lastind = 0;
    for (int i = 0; i < T; i++) {
        int step = 128;
        for (int j = 0; j < step; j++) {
            lastind = i*step+j;
            beat_period[lastind] = bestpath[i];
        }
    }

    for (int i = lastind; i < int(beat_period.size()); i++) {
        beat_period[i] = beat_period[lastind];
    }

    for (int i = 0; i < int(beat_period.size()); i++) {
        tempi.push_back((60. * m_rate / m_increment)/beat_period[i]);
    }
}

double
TempoTrackV2::get_max_val(const d_vec_t &df)
{
    double maxval = 0.;
    int df_len = int(df.size());

    for (int i = 0; i < df_len; i++) {
        if (maxval < df[i]) {
            maxval = df[i];
        }
    }

    return maxval;
}

int
TempoTrackV2::get_max_ind(const d_vec_t &df)
{
    double maxval = 0.;
    int ind = 0;
    int df_len = int(df.size());

    for (int i = 0; i < df_len; i++) {
        if (maxval < df[i]) {
            maxval = df[i];
            ind = i;
        }
    }

    return ind;
}

void
TempoTrackV2::normalise_vec(d_vec_t &df)
{
    double sum = 0.;
    int df_len = int(df.size());

    for (int i = 0; i < df_len; i++) {
        sum += df[i];
    }

    for (int i = 0; i < df_len; i++) {
        df[i]/= (sum + EPS);
    }
}

void
TempoTrackV2::calculateBeats(const vector<double> &df,
                             const vector<double> &beat_period,
                             vector<double> &beats, double alpha, double tightness)
{
    if (df.empty() || beat_period.empty()) return;

    int df_len = int(df.size());

    d_vec_t cumscore(df_len);
    i_vec_t backlink(df_len);
    d_vec_t localscore(df_len);

    for (int i = 0; i < df_len; i++) {
        localscore[i] = df[i];
        backlink[i] = -1;
    }

    for (int i = 0; i < df_len; i++) {
        int prange_min = -2*beat_period[i];
        int prange_max = round(-0.5*beat_period[i]);

        int txwt_len = prange_max - prange_min + 1;
        d_vec_t txwt (txwt_len);
        d_vec_t scorecands (txwt_len);

        for (int j = 0; j < txwt_len; j++) {
            double mu = double(beat_period[i]);
            txwt[j] = exp( -0.5*pow(tightness * log((round(2*mu)-j)/mu),2));

            int cscore_ind = i + prange_min + j;
            if (cscore_ind >= 0) {
                scorecands[j] = txwt[j] * cumscore[cscore_ind];
            }
        }

        double vv = get_max_val(scorecands);
        int xx = get_max_ind(scorecands);

        cumscore[i] = alpha*vv + (1.-alpha)*localscore[i];
        backlink[i] = i+prange_min+xx;
    }

    d_vec_t tmp_vec;
    for (int i = df_len - beat_period[beat_period.size()-1] ; i < df_len; i++) {
        tmp_vec.push_back(cumscore[i]);
    }

    int startpoint = get_max_ind(tmp_vec) +
        df_len - beat_period[beat_period.size()-1] ;

    if (startpoint >= int(backlink.size())) {
        startpoint = int(backlink.size()) - 1;
    }

    i_vec_t ibeats;
    ibeats.push_back(startpoint);
    while (backlink[ibeats.back()] > 0) {
        int b = ibeats.back();
        if (backlink[b] == b) break;
        ibeats.push_back(backlink[b]);
    }

    for (int i = 0; i < int(ibeats.size()); i++) {
        beats.push_back(double(ibeats[ibeats.size() - i - 1]));
    }
}
