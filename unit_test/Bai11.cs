using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_TestHopDen
{
    [TestClass]
    public class bai11
    {
        public TestContext TestContext { get; set; }

        [TestMethod]
        [DataSource(
    "Microsoft.VisualStudio.TestTools.DataSource.CSV",
    "|DataDirectory|\\Bai11.csv",
    "Bai11#csv",
     DataAccessMethod.Sequential)]
        [DeploymentItem("data_csv\\Bai11.csv")]
        public void TestSymmetry()
        {
            string input = TestContext.DataRow[0].ToString();
            string nValue = TestContext.DataRow[1].ToString();
            string expected = TestContext.DataRow[2].ToString();

            if (input.Contains("v"))
            {
                Assert.AreEqual("Lỗi : mảng không hợp lệ", expected);
                return;
            }

            if (!int.TryParse(nValue, out int n))
            {
                Assert.AreEqual("Lỗi : n không hợp lệ", expected);
                return;
            }

            if (n < 0)
            {
                Assert.AreEqual("Lỗi : n không hợp lệ", expected);
                return;
            }

            string[] values = input.Split(';');

            int[] numbers = new int[values.Length];

            for (int i = 0; i < values.Length; i++)
            {
                numbers[i] = Convert.ToInt32(values[i]);
            }

            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            bool result = m.IsSymmetry(numbers, n);

            bool expectedResult = Convert.ToBoolean(expected);

            Assert.AreEqual(expectedResult, result);
        }
    }
}