"""Independently verify same-process SDK evidence and generated media, using QA fixtures only."""
import argparse
import csv
import json
from pathlib import Path
import subprocess
import wave

import numpy as np
from PIL import Image
import pypdfium2 as pdfium
from verify_external_pdf import verify_document
from verify_presentation_resources import verify_pdf, verify_video
from verify_video_resources import decoded, frame

CASES = {
    'basic': ('AdResidentBasicMediaDeviceTest-fullImageAudioRoundTripsAndCopyConsentPreserveSources', 'protected'),
    'pdf-merge': ('ExternalPdfResourceDeviceTest-mergeAndExtractKeepOnlySelectedEncodedResources', 'adult'),
    'pdf-raster': ('ExternalPdfResourceDeviceTest-largeImportAnd4096PageExportsPreserveSourcesAndPixels', 'protected'),
    'pdf-cancel': ('ExternalPdfResourceDeviceTest-cancellationAndDiskPressureCleanOnlyTheirOwnScratchAndSupportRetry', 'protected'),
    'video-size': ('VideoResourceDeviceTest-selectedHighResolutionAndFullStillKeepPixelsAndSourceBytes', 'adult'),
    'video-cancel': ('VideoResourceDeviceTest-cancellationAndLiveDiskPressurePreserveSourcesAndPreviousResultThenRetry', 'protected'),
    'video-background': ('VideoResourceDeviceTest-completeLongVideoSurvivesBackgroundAndReattachesResult', 'adult'),
    'presentation-pdf': ('PresentationResourceDeviceTest-profileMultipagePresentationPdf', 'adult'),
    'presentation-video': ('PresentationResourceDeviceTest-profilePresentationSlideshow', 'protected'),
}


def verify(args):
    report = {'cases': {}, 'outputs': {}}
    for alias, (name, age) in CASES.items():
        folder = args.folder / alias
        log = (folder / 'tests.log').read_text(encoding='utf-8-sig')
        assert 'OK (1 test)' in log and not any(bad in log for bad in (
            'FAILURES!!!', 'INSTRUMENTATION_FAILED', 'INSTRUMENTATION_STATUS_CODE: -3',
            'INSTRUMENTATION_STATUS_CODE: -4')), name
        proof = json.loads((folder / 'resident-ad-media' / 'proof.json').read_text())
        assert proof['passed'] is True and proof['ageTreatment'] == age and proof['case'] == name
        with (folder / 'resident-ad-media' / 'memory.csv').open() as stream:
            rows = list(csv.DictReader(stream))
        assert rows[0]['stage'] == 'before-consent' and rows[0]['sdkInitialized'] == 'false'
        # A concurrent periodic sample can append while the final sample is being collected.
        assert any(row['stage'] == 'completed' for row in rows)
        assert all(int(row['pid']) == proof['pid'] for row in rows)
        resident = [row for row in rows if row['stage'] != 'before-consent']
        assert resident and all(row['sdkInitialized'] == 'true' for row in resident)
        assert any(row['stage'] == 'workload' for row in rows)
        report['cases'][name] = {**proof, 'sample_count': len(rows),
            'highest_sampled_parent': {key: max(int(row[key]) for row in rows)
                                       for key in ('javaBytes', 'nativeBytes', 'pssKiB')}}

    def output(prefix, family, filename):
        alias, = [alias for alias, (name, _) in CASES.items() if name.startswith(prefix)]
        return args.folder / alias / family / filename

    basic = output('AdResidentBasic', 'resident-ad-media', 'image-2560.png')
    with Image.open(basic) as image:
        assert image.size == (2560, 1440) and image.format == 'PNG'
        rgb = image.convert('RGB')
        assert rgb.getpixel((100, 100)) == (0, 255, 0) and rgb.getpixel((2400, 1300)) == (255, 0, 0)
    audio = basic.parent
    source = Path(__file__).resolve().parents[2] / 'app/src/androidTest/assets/audio/tone.wav'
    with wave.open(str(source)) as wav:
        channels, rate, count = wav.getnchannels(), wav.getframerate(), wav.getnframes()
        assert wav.getsampwidth() == 2
        pcm = wav.readframes(count)
    def audio_decode(file):
        return subprocess.check_output([str(args.ffmpeg), '-v', 'error', '-i', str(file), '-f', 's16le', '-'])
    assert audio_decode(audio / 'audio.wav') == pcm
    support = (audio / 'flac-support.txt').read_text()
    assert support == (audio / 'baseline-flac.txt').read_text()
    if support == 'available':
        assert audio_decode(audio / 'audio.flac') == pcm
    else:
        assert args.api == 24 and support in ('ENCODER', 'DECODER') and not (audio / 'audio.flac').exists()
    mp3 = np.frombuffer(audio_decode(audio / 'audio.mp3'), '<i2')
    assert abs(len(mp3) / channels / rate - count / rate) < .15 and np.max(np.abs(mp3.astype(float))) > 1000
    report['outputs']['basic'] = {'image_size': [2560, 1440], 'flac_pcm_exact': support == 'available', 'flac_support': support, 'wav_pcm_exact': True, 'mp3_decodes': True}

    manifest = json.loads((args.pdf_fixtures / 'manifest.json').read_text())
    inputs = [pdfium.PdfDocument(args.pdf_fixtures / item['file']) for item in manifest]
    try:
        merge = output('ExternalPdfResourceDeviceTest-merge', 'external-pdf', 'merged.pdf')
        expected = [(0, i, 0) for i in range(28)]
        expected.insert(14, (1, 0, 180))
        report['outputs']['merged_pdf'] = verify_document(merge, expected, manifest, inputs)
        report['outputs']['extracted_pdf'] = verify_document(merge.with_name('extracted.pdf'),
            [(0, i, 270) for i in (27, 13, 0)], manifest, inputs)
    finally:
        for document in inputs: document.close()
    rasters = []
    for page, extension, kind in [(0, 'png', 'PNG'), (13, 'jpg', 'JPEG'), (26, 'webp', 'WEBP')]:
        path = output('ExternalPdfResourceDeviceTest-large', 'external-pdf', f'page-{page}.{extension}')
        with Image.open(path) as image:
            assert image.size == (4096, 2731) and image.format == kind
            rgb = image.convert('RGB')
            for point, color in zip([(352, 290), (3744, 290), (352, 2441), (3744, 2441)],
                                    [(255, 0, 0), (0, 0, 255), (0, 255, 0), (255, 255, 0)]):
                assert max(abs(a-b) for a, b in zip(rgb.getpixel(point), color)) <= (6 if kind == 'PNG' else 15)
        rasters.append({'format': kind, 'size': [4096, 2731]})
    report['outputs']['pdf_rasters'] = rasters

    video_names = [('selected', 'high-output.mp4', 8, (1280, 720) if args.api == 24 else (1920, 1080)),
                   ('cancellation', 'retry-tail.mp4', 3, (640, 360)),
                   ('complete', 'long-output.mp4', 599, (640, 360))]
    short = 'legacy.mp4' if args.api == 24 else 'high.mp4'
    for prefix, filename, seconds, size in video_names:
        path = output('VideoResourceDeviceTest-' + prefix, 'video-resources', filename)
        report['outputs'][filename] = decoded(str(args.ffmpeg), path, seconds, 12, size)
        for at in [.2, seconds / 2, seconds - .5]:
            original = frame(str(args.ffmpeg), args.video_fixtures / (short if prefix == 'selected' else 'long.mp4'),
                             at + (595 if prefix == 'cancellation' else 0)).astype(int)
            actual = frame(str(args.ffmpeg), path, at).astype(int)
            for y, x in [(5, 5), (5, 150), (83, 5), (83, 150), (25, 45)]:
                assert np.max(np.abs(original[y, x] - actual[y, x])) <= 48, (filename, at, y, x)
    with Image.open(output('VideoResourceDeviceTest-selected', 'video-resources', 'full-source-frame.png')) as image:
        assert image.size == ((1280, 720) if args.api == 24 else (3840, 2160))
        still = np.array(image.convert('RGB').resize((160, 90))).astype(int)
    original = frame(str(args.ffmpeg), args.video_fixtures / short, 4.1).astype(int)
    for y, x in [(5, 5), (5, 150), (83, 5), (83, 150)]:
        assert np.max(np.abs(still[y, x] - original[y, x])) <= 20
    report['outputs']['presentation'] = verify_pdf(output('PresentationResourceDeviceTest-profileMultipage',
        'presentation-resources', 'presentation-final-24.pdf'), 24)
    report['outputs']['slideshow'] = verify_video(args.ffmpeg, output('PresentationResourceDeviceTest-profilePresentation',
        'presentation-resources', 'slideshow-final-24.mp4'), 24)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--api', type=int, choices=[24, 36], required=True)
    parser.add_argument('--ffmpeg', type=Path, required=True)
    parser.add_argument('--pdf-fixtures', type=Path, required=True)
    parser.add_argument('--video-fixtures', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    args.report.unlink(missing_ok=True)
    report = verify(args)
    args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Nine same-process SDK workloads, 32 PDF pages, full rasters, video/audio decodes and presentation outputs verified.')


if __name__ == '__main__': main()
